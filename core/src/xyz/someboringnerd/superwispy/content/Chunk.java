package xyz.someboringnerd.superwispy.content;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.PerformanceCounter;
import lombok.AccessLevel;
import lombok.Getter;
import net.npcinteractive.TranscendanceEngine.Managers.AudioManager;
import net.npcinteractive.TranscendanceEngine.Managers.LogManager;
import net.npcinteractive.TranscendanceEngine.Managers.RoomManager;
import net.npcinteractive.TranscendanceEngine.Map.DebugCube;
import net.npcinteractive.TranscendanceEngine.Util.RenderUtil;
import xyz.someboringnerd.superwispy.GlobalData;
import xyz.someboringnerd.superwispy.content.structures.Tree;
import xyz.someboringnerd.superwispy.entities.PlayerEntity;
import xyz.someboringnerd.superwispy.managers.BlockManager;
import xyz.someboringnerd.superwispy.rooms.GameRoom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class Chunk extends Thread
{
    public Block[][] blockList = new Block[16][256];

    @Getter(AccessLevel.PUBLIC)
    private int offset;

    int prevY = 0;

    int[] topBlock;

    private int leftY, rightY;

    boolean generateRight = false;

    public Chunk instance;

    public Chunk(int offset)
    {
        instance = this;
        this.offset = offset;
        LogManager.print("Offset = " + offset);
        // chunk a gauche de celui qu'on genère
        if(GameRoom.getInstance().doesChunkExistAtOffset(offset - 512))
        {
            LogManager.print("Generated chunk on the right of an existing chunk");
            LogManager.print("Top left of previous chunk : " + GameRoom.getInstance().getChunkAtOffset(offset - 512).rightY);
            generateRight = true;
            Generate(GameRoom.getInstance().getChunkAtOffset(offset - 512).rightY - 3, GameRoom.getInstance().getChunkAtOffset(offset - 512).rightY + 3);
        }
        // chunk a droite de celui qu'on genère
        else if(GameRoom.getInstance().doesChunkExistAtOffset(offset + 512))
        {
            LogManager.print("Generated chunk on the left of an existing chunk");
            generateRight = false;
            LogManager.print("Top right of previous chunk : " + GameRoom.getInstance().getChunkAtOffset(offset + 512).leftY);
            Generate(GameRoom.getInstance().getChunkAtOffset(offset + 512).leftY - 3, GameRoom.getInstance().getChunkAtOffset(offset + 512).leftY + 3);
        }else
        {
            if(offset != 0)
            {
                LogManager.error("A chunk was generated without being connected to another chunk, THIS SHOULD NEVER HAPPEN !!");
            }
            Generate(57, 63);
        }
    }

    boolean isPlayerInChunk()
    {
        return GameRoom.getInstance().getPlayer().getX() >= offset && offset + 16 * 32 >= GameRoom.getInstance().getPlayer().getX();
    }

    int y = 0;

    long RenderTime, GenerateTime;

    void Generate(int Lower, int Upper)
    {
        GeneratingDone.set(false);
        Thread thread = new Thread()
        {
            public void run()
            {

                for(int x = 0; x < 16; x++)
                {
                    for(int y = 0; y < 256; y++)
                    {
                        blockList[x][y] = new Block(new Vector2(x, y), 0, instance);
                    }
                }

                topBlock = new int[16];
                if(offset == 0)
                {
                    y = 63;
                }
                for(int i = 0; i < 16; i++)
                {
                    if(i == 0)
                    {
                        y = Upper - 3;
                        if(generateRight)
                            leftY = y;
                        else
                            rightY = y;
                    }
                    if(i == 15)
                    {
                        if(generateRight)
                            rightY = y;
                        else
                            leftY = y;
                    }
                    int rng = new Random().nextInt(0, 100);
                    if(rng <= 20)
                    {
                        y--;
                    }
                    else if(rng >= 80)
                    {
                        y++;
                    }

                    if(y <= Lower)
                    {
                        y = Lower;
                    }

                    if(y >= Upper)
                    {
                        y = Upper;
                    }

                    if(y <= 54)
                    {
                        y = 54;
                    }

                    if(y >= 70)
                    {
                        y = 70;
                    }


                    topBlock[(generateRight ? i : 15 - i)] = y;

                    int realX = (generateRight ? i : 15 - i);


                    blockList[realX][y] = (new Block(new Vector2(realX, y), 1, instance));

                    for(int j = y - 1; j >= 0; j--)
                    {
                        if(j >= y - 3)
                        {
                            blockList[realX][j] = (new Block(new Vector2(realX, j), 2, instance));
                        }
                        else if(j == 0)
                        {
                            blockList[realX][j] = (new Block(new Vector2(realX, j), 6, instance));
                        }
                        else{
                            blockList[realX][j] = (new Block(new Vector2(realX, j), 3, instance));
                        }
                    }

                    int tempY = y + 1;

                    if(rng < 60 && rng > 50 && i < 10 && i > 5 && offset != 0)
                    {
                        Tree tree = new Tree();

                        for(int x = 0; x < tree.content.length; x++)
                        {
                            for(int _y = 0; _y < tree.content[x].length; _y++)
                            {
                                if(tree.content[x][_y] != 0)
                                {
                                    blockList[i + x][tempY + _y] = new Block(new Vector2(i + x, tempY + _y), tree.content[x][_y], instance);
                                }
                            }
                        }
                    }
                }


                GeneratingDone.set(true);
            }
        };

        thread.start();
    }

    public boolean bodiesGenerated = false;

    public boolean regeneratingBodies;

    @Deprecated(since = "16/10/23 : Les chunks n'ont plus a supprimer leurs colliders.")
    private void DestroyBodies()
    {

    }

    // @fix : Une fois créé, un body ne peut être supprimé sinon ça marche pas les trucs
    public void GenerateBodies()
    {
        for(Block block : getBlockAsList())
        {
            if(block != null)
            {
                if(block.body != null){
                    RoomManager.world.destroyBody(block.body);
                    block.body = null;
                }
                if (hasVisibleSide(block.getChunkPosition()))
                {
                    if (block.getId() != 0)
                    {
                        block.createBody(RoomManager.world);
                    }
                }
            }
        }

        bodiesGenerated = true;
    }

    private boolean hasVisibleSide(Vector2 chunkPosition)
    {
        try
        {
            return blockList[(int) (chunkPosition.x - 1)][(int) chunkPosition.y] == null ||
                    blockList[(int) (chunkPosition.x + 1)][(int) chunkPosition.y] == null ||
                    blockList[(int) (chunkPosition.x)][(int) chunkPosition.y + 1] == null ||
                    blockList[(int) (chunkPosition.x)][(int) chunkPosition.y - 1] == null ||
                    blockList[(int) (chunkPosition.x - 1)][(int) chunkPosition.y].getId() == 0 ||
                    blockList[(int) (chunkPosition.x + 1)][(int) chunkPosition.y].getId() == 0 ||
                    blockList[(int) (chunkPosition.x)][(int) chunkPosition.y + 1].getId() == 0 ||
                    blockList[(int) (chunkPosition.x)][(int) chunkPosition.y - 1].getId() == 0;

        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            return true;
        }
    }

    public List<Block> getBlockAsList()
    {
        List<Block> blocks = new ArrayList<>();
        for(int x = 0; x < 16; x++)
        {
            for(int y = 0; y < 256; y++)
            {
                if(blockList[x][y] != null)
                {
                    blocks.add(blockList[x][y]);
                }
            }
        }

        return blocks;
    }

    public Block getBlockAtCoordinates(Vector2 blockCoordinate)
    {
        try
        {
            return blockList[(int) (blockCoordinate.x / 32 - offset)][(int) (blockCoordinate.y / 32)] != null ? blockList[(int) (blockCoordinate.x / 32)][(int) (blockCoordinate.y / 32)] : null;
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            return null;
        }
    }

    AtomicBoolean GeneratingDone = new AtomicBoolean(false);
    PerformanceCounter counter;
    public void Draw(Batch batch)
    {
        if(!GeneratingDone.get()) return;

        if(!bodiesGenerated)
        {
            GenerateBodies();
        }

        if(isPlayerInChunk())
        {
            GameRoom.getInstance().Generate(offset);
            GenerateBodies();
            if(GlobalData.showChunkPlayerIsIn)
                batch.setColor(0, 1, 0, 1);
        }

        List<Block> toDelete = new ArrayList<>();
        for (Block block : getBlockAsList())
        {
            if(block != null)
            {
                if (block.markForDelete)
                {
                    toDelete.add(block);
                }
                else
                {
                    block.draw(batch, 1.0f);
                }
            }
        }

        if(!toDelete.isEmpty())
        {
            for (Block block : getBlockAsList())
            {
                if(block != null)
                {
                    if (block.body != null)
                    {
                        if(block.markForDelete)
                        {
                            RoomManager.world.destroyBody(block.body);

                            if (blockList[(int) block.getChunkPosition().x][(int) block.getChunkPosition().y] != null)
                            {
                                AudioManager.getInstance().playAudio("sfx/explosion");
                                blockList[(int) block.getChunkPosition().x][(int) block.getChunkPosition().y] = new Block(new Vector2((int) block.getChunkPosition().x,(int) block.getChunkPosition().y), 0, instance);
                            }
                        }
                    }
                }
            }

            bodiesGenerated = false;
        }

        batch.setColor(1, 1, 1, 1);

        if(GlobalData.displayDebugInformation)
            RenderUtil.DrawText(batch, "Chunk " + offset + " to " + (offset + 512), new Vector2(offset, 70*32), RenderUtil.Deter52px);
    }
}