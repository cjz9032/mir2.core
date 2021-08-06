package mir2.core;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import javax.imageio.ImageIO;

import com.github.jootnet.mir2.core.Texture;
import com.github.jootnet.mir2.core.image.ImageInfo;
import com.github.jootnet.mir2.core.image.ImageLibraries;
import com.github.jootnet.mir2.core.image.ImageLibrary;
import com.github.jootnet.mir2.core.map.Map;
import com.github.jootnet.mir2.core.map.MapTileInfo;
import com.github.jootnet.mir2.core.map.Maps;


/**
 * 导出TiledMap格式的TMX数据
 *
 * @author 云
 */
public class TiledMapExporter {

    static String OUT_DIR = "E:\\temp\\M3";
    static String DATA_DIR = "E:\\176\\Data\\";
    static String DATA_MAP = "E:\\176\\Map\\";
//static String DATA_DIR = "D:\\10-mir2\\client\\Data\\";

    static String LINE_SEPARATOR = System.getProperty("line.separator");

    static void exportTsx(String dir, String ilName, ImageLibrary il) throws IOException {
        if (new File(dir, ilName + ".tsx").exists()) return; // XXX
        int maxWidth = 0;
        int maxHeight = 0;
        for (int i = 0; i < il.count(); ++i) {
            maxWidth = Math.max(maxWidth, il.info(i).getWidth());
            maxHeight = Math.max(maxHeight, il.info(i).getHeight());
        }
        File imgOutDir = new File(dir, ilName);
        if (!imgOutDir.exists())
            imgOutDir.mkdirs();
        // 做一个空图片
        BufferedImage emptyImg = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
        ((DataBufferByte) emptyImg.getRaster().getDataBuffer()).getData()[0] = -1;
        ImageIO.write(emptyImg, "png", new File(imgOutDir, "empty.png"));
        StringBuilder tsxXml = new StringBuilder();
        tsxXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        tsxXml.append(LINE_SEPARATOR);
        tsxXml.append("<tileset name=\"")
                .append(ilName)
                .append("\" tilewidth=\"")
                .append(maxWidth)
                .append("\" tileheight=\"")
                .append(maxHeight)
                .append("\" tilecount=\"")
                .append(il.count())
                .append("\" columns=\"0\">");
        tsxXml.append(LINE_SEPARATOR);
        tsxXml.append(" <grid orientation=\"orthogonal\" width=\"1\" height=\"1\"/>");
        tsxXml.append(LINE_SEPARATOR);

        for (int i = 0; i < il.count(); ++i) {
            tsxXml.append(" <tile id=\"").append(i).append("\">");
            tsxXml.append(LINE_SEPARATOR);
            Texture tex = il.tex(i);
            if (tex.empty()) {
                tsxXml.append("  <image width=\"1\" height=\"1\" source=\"").append(ilName).append("/empty.png\"/>");
            } else {
                ImageInfo ii = il.info(i);
                BufferedImage bi = new BufferedImage(ii.getWidth(), ii.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
                byte[] pixels = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
                for (int j = 0; j < ii.getWidth() * ii.getHeight(); ++j) {
                    pixels[j * 4 + 3] = tex.getRGBs()[j * 3];
                    pixels[j * 4 + 2] = tex.getRGBs()[j * 3 + 1];
                    pixels[j * 4 + 1] = tex.getRGBs()[j * 3 + 2];
                    if (pixels[j * 4 + 3] != 0 || pixels[j * 4 + 1] != 0 || pixels[j * 4 + 2] != 0)
                        pixels[j * 4] = -1;
                }
                ImageIO.write(bi, "png", new File(imgOutDir, i + ".png"));
                tsxXml.append("  <image width=\"")
                        .append(ii.getWidth())
                        .append("\" height=\"")
                        .append(ii.getHeight())
                        .append("\" source=\"")
                        .append(ilName)
                        .append("/")
                        .append(i)
                        .append(".png\"/>");
            }
            tsxXml.append(LINE_SEPARATOR);
            tsxXml.append(" </tile>");
            tsxXml.append(LINE_SEPARATOR);
        }

        tsxXml.append("</tileset>");
        Files.write(new File(dir, ilName + ".tsx").toPath(), tsxXml.toString().getBytes(), StandardOpenOption.CREATE);
    }

    static void exportTmx(String dir, String mapName, Map map) throws IOException {
        if (new File(dir, mapName + ".tsx").exists()) return; // XXX
        int gidx = 0; // 素材索引
        // tiles
        int[] tileGIDOffsets = new int[1000];
        for (int h = 0; h < map.getHeight(); h += 2) {
            for (int w = 0; w < map.getWidth(); w += 2) {
                MapTileInfo mti = map.getTiles()[w][h];
                if (mti.isHasBng()) {
                    if (tileGIDOffsets[mti.getBngFileIdx()] == 0)
                        tileGIDOffsets[mti.getBngFileIdx()] = gidx++ * 62767 + 1;
                }
            }
        }
        // smtiles
        int[] smTileGIDOffsets = new int[1000];
        for (int h = 0; h < map.getHeight(); ++h) {
            for (int w = 0; w < map.getWidth(); ++w) {
                MapTileInfo mti = map.getTiles()[w][h];
                if (mti.isHasMid()) {
                    if (smTileGIDOffsets[mti.getMidFileIdx()] == 0)
                        smTileGIDOffsets[mti.getMidFileIdx()] = gidx++ * 62767 + 1;
                }
            }
        }
        // objs
        int[] objGIDOffsets = new int[1000];
        for (int h = 0; h < map.getHeight(); ++h) {
            for (int w = 0; w < map.getWidth(); ++w) {
                MapTileInfo mti = map.getTiles()[w][h];
                if (mti.isHasObj()) {
                    int a = mti.getObjFileIdx();
                    if(a < 0){
                                System.out.println(                        mapName + ':'+'h'+','+w+'-'+a);
                        a = 0;
                        // throw new Error("error");
                    }

                    if (objGIDOffsets[a] == 0)
                        objGIDOffsets[a] = gidx++ * 62767 + 1;
                }
            }
        }


        StringBuilder tmxXml = new StringBuilder();
        tmxXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        tmxXml.append(LINE_SEPARATOR);
        tmxXml.append("<map version=\"1.0\" tiledversion=\"1.1.2\" orientation=\"orthogonal\" renderorder=\"right-down\" width=\"")
                .append(map.getWidth())
                .append("\" height=\"")
                .append(map.getHeight())
                .append("\" tilewidth=\"48\" tileheight=\"32\" infinite=\"0\" nextobjectid=\"1\">");
        tmxXml.append(LINE_SEPARATOR);
        for (int i = 0; i < tileGIDOffsets.length; ++i) {
            if (tileGIDOffsets[i] != 0) {
                tmxXml.append(" <tileset firstgid=\"")
                        .append(tileGIDOffsets[i])
                        .append("\" source=\"tiles");
                if (i != 0)
                    tmxXml.append(i);
                tmxXml.append(".tsx\"/>");
                tmxXml.append(LINE_SEPARATOR);
            }
        }
        for (int i = 0; i < smTileGIDOffsets.length; ++i) {
            if (smTileGIDOffsets[i] != 0) {
                tmxXml.append(" <tileset firstgid=\"")
                        .append(smTileGIDOffsets[i])
                        .append("\" source=\"smtiles");
                if (i != 0)
                    tmxXml.append(i);
                tmxXml.append(".tsx\"/>");
                tmxXml.append(LINE_SEPARATOR);
            }
        }
        //tmxXml.append(" <tileset firstgid=\"1\" source=\"Tiles.tsx\"/>");
        //tmxXml.append(LINE_SEPARATOR);
        //tmxXml.append(" <tileset firstgid=\"32768\" source=\"SmTiles.tsx\"/>"); // 预设每个TSX有32767张图，浪费空间我不怕
        //tmxXml.append(LINE_SEPARATOR);
        for (int i = 0; i < objGIDOffsets.length; ++i) {
            if (objGIDOffsets[i] != 0) {
                tmxXml.append(" <tileset firstgid=\"")
                        .append(objGIDOffsets[i])
                        .append("\" source=\"objects");
                if (i != 0)
                    tmxXml.append(i);
                tmxXml.append(".tsx\"/>");
                tmxXml.append(LINE_SEPARATOR);
            }
        }
        int cllisionGid = gidx++ * 32767 + 1;
        tmxXml.append(" <tileset firstgid=\"" + (cllisionGid) + "\" name=\"collision\" tilewidth=\"48\" tileheight=\"32\" tilecount=\"1\">\n" +
                "  <image source=\"collision-tile.png\" trans=\"000000\" width=\"48\" height=\"32\"/>\n" +
                " </tileset>");

        tmxXml.append(" <layer name=\"base\" width=\"")
                .append(map.getWidth())
                .append("\" height=\"")
                .append(map.getHeight())
                .append("\">");
        tmxXml.append(LINE_SEPARATOR);
        tmxXml.append("  <data encoding=\"csv\">");
        tmxXml.append(LINE_SEPARATOR);
        for (int w = 0; w < map.getWidth(); ++w) {
            tmxXml.append("0,"); // 第一行木有大砖块
        }
        tmxXml.append(LINE_SEPARATOR);
        for (int h = 1; h < map.getHeight(); ++h) {
            for (int w = 0; w < map.getWidth(); ++w) {
                MapTileInfo mti = map.getTiles()[w][h - 1];
                if (mti.isHasBng()) {
                    tmxXml.append(mti.getBngImgIdx() + tileGIDOffsets[mti.getBngFileIdx()]);
                } else {
                    tmxXml.append("0");
                }
                if (w != map.getWidth() - 1 || h != map.getHeight() - 1)
                    tmxXml.append(",");
            }
            tmxXml.append(LINE_SEPARATOR);
        }
        tmxXml.append("  </data>");
        tmxXml.append(LINE_SEPARATOR);
        tmxXml.append(" </layer>");
        tmxXml.append(LINE_SEPARATOR);

        tmxXml.append(" <layer name=\"mid\" width=\"")
                .append(map.getWidth())
                .append("\" height=\"")
                .append(map.getHeight())
                .append("\">");
        tmxXml.append(LINE_SEPARATOR);
        tmxXml.append("  <data encoding=\"csv\">");
        tmxXml.append(LINE_SEPARATOR);
        for (int h = 0; h < map.getHeight(); ++h) {
            for (int w = 0; w < map.getWidth(); ++w) {
                MapTileInfo mti = map.getTiles()[w][h];
                if (mti.isHasMid()) {
                    tmxXml.append(mti.getMidImgIdx() + smTileGIDOffsets[mti.getMidFileIdx()]);
                } else {
                    tmxXml.append("0");
                }
                if (w != map.getWidth() - 1 || h != map.getHeight() - 1)
                    tmxXml.append(",");
            }
            tmxXml.append(LINE_SEPARATOR);
        }
        tmxXml.append("  </data>");
        tmxXml.append(LINE_SEPARATOR);
        tmxXml.append(" </layer>");
        tmxXml.append(LINE_SEPARATOR);

        tmxXml.append(" <layer name=\"obj\" width=\"")
                .append(map.getWidth())
                .append("\" height=\"")
                .append(map.getHeight())
                .append("\">");
        tmxXml.append(LINE_SEPARATOR);
        tmxXml.append("  <data encoding=\"csv\">");
        tmxXml.append(LINE_SEPARATOR);
        for (int h = 0; h < map.getHeight(); ++h) {
            for (int w = 0; w < map.getWidth(); ++w) {
                MapTileInfo mti = map.getTiles()[w][h];
                if (mti.isHasObj() && !mti.isHasAni()) {
                    tmxXml.append(mti.getObjImgIdx() + objGIDOffsets[mti.getObjFileIdx() < 0 ? 0 : mti.getObjFileIdx()]);
                } else {
                    tmxXml.append("0");
                }
                if (w != map.getWidth() - 1 || h != map.getHeight() - 1)
                    tmxXml.append(",");
            }
            tmxXml.append(LINE_SEPARATOR);
        }
        tmxXml.append("  </data>");
        tmxXml.append(LINE_SEPARATOR);
        tmxXml.append(" </layer>");

        tmxXml.append(LINE_SEPARATOR);
        // collision
        tmxXml.append(" <layer name=\"Collisions\" width=\"")
                .append(map.getWidth())
                .append("\" height=\"")
                .append(map.getHeight())
                .append("\" ")
                .append("opacity=\"0.2\"")
                .append(">");
        tmxXml.append(LINE_SEPARATOR);
        tmxXml.append("  <data encoding=\"csv\">");
        tmxXml.append(LINE_SEPARATOR);
        for (int h = 0; h < map.getHeight(); ++h) {
            for (int w = 0; w < map.getWidth(); ++w) {
                MapTileInfo mti = map.getTiles()[w][h];
                if (!mti.isCanWalk()) {
                    tmxXml.append(cllisionGid);
                } else {
                    tmxXml.append("0");
                }
                if (w != map.getWidth() - 1 || h != map.getHeight() - 1)
                    tmxXml.append(",");
            }
            tmxXml.append(LINE_SEPARATOR);
        }
        tmxXml.append("  </data>");
        tmxXml.append(LINE_SEPARATOR);
        tmxXml.append(" </layer>");


        tmxXml.append("</map>");
        // ani
        tmxXml.append(LINE_SEPARATOR);

        tmxXml.append("<custom>");
        tmxXml.append(LINE_SEPARATOR);

        tmxXml.append("<animationPoints>");
        tmxXml.append(LINE_SEPARATOR);
        tmxXml.append("  <data read=\"mapY@mapY,frames,filename,imgIdx,offsetX@offsetY\" encoding=\"nothing\">");
        tmxXml.append(LINE_SEPARATOR);
        for (int h = 0; h < map.getHeight(); ++h) {
            for (int w = 0; w < map.getWidth(); ++w) {
                MapTileInfo mti = map.getTiles()[w][h];
                if (mti.isHasAni()) {
                    int fileIdx = mti.getObjFileIdx();
                    String nameIdx = (fileIdx == 0 ? "" : (fileIdx + ""));
                    String fileName = "objects" + nameIdx;
                    tmxXml.append(
                            w+ "@"+ h + "," +
                            mti.getAniFrame() + ","+
                            fileName + ","+
                            mti.getObjImgIdx() + ","

                    );

//					f,x,y
                    Arrays.stream(new File(DATA_DIR).list((f, _fn) ->
                            (_fn.toLowerCase().startsWith(fileName+".wil")) &&
                                    (_fn.toLowerCase().endsWith("wil")))).parallel().forEach(fn -> {
                        fn = fn.substring(0, fn.length() - 4).toLowerCase();
                        ImageLibrary il_tiles = ImageLibraries.get(fn, DATA_DIR + fn);
                        ImageInfo info = il_tiles.info(mti.getObjImgIdx());

                        tmxXml.append(
                                        info.getOffsetX() + "@"+ info.getOffsetY() + ","

                        );
                    });
                    tmxXml.append(LINE_SEPARATOR);

                }
//				if(w != map.getWidth() - 1 || h != map.getHeight() - 1)
//					tmxXml.append(",");
            }
//			tmxXml.append(LINE_SEPARATOR);
        }
        tmxXml.append("  </data>");
        tmxXml.append(LINE_SEPARATOR);
        tmxXml.append("</animationPoints>");
        tmxXml.append(LINE_SEPARATOR);

        tmxXml.append(" </custom>");


        Files.write(new File(dir, mapName + ".tmx").toPath(), tmxXml.toString().getBytes(), StandardOpenOption.CREATE);
    }

    public static void main(String[] args) throws IOException {

		Arrays.stream(new File(DATA_DIR).list((f, _fn) ->
				(!_fn.toLowerCase().startsWith("weapon.")) &&
				(_fn.toLowerCase().endsWith("wzl") || _fn.toLowerCase().endsWith("wil") || _fn.toLowerCase().endsWith("wixxx")))).parallel().forEach(fn -> {
			fn = fn.substring(0, fn.length() - 4).toLowerCase();
			try {
                ImageLibrary il_tiles = ImageLibraries.get(fn, DATA_DIR + fn);

                exportTsx(OUT_DIR, fn, il_tiles);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

        Arrays.stream(new File(DATA_MAP).list((f, _fn) ->(_fn.endsWith(".map")))).parallel().forEach(fn -> {
			String name = fn.substring(0, fn.length() - 4);
			try {
                exportTmx(OUT_DIR, name, Maps.get(name,  DATA_MAP + fn));

            } catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

//		exportTmx(OUT_DIR, "T118", Maps.get("T118", "D:\\10-mir2\\client\\Map\\T118.map"));
//        exportTmx(OUT_DIR, "0", Maps.get("0", "F:\\mirall\\176client\\Map\\0.map"));
//		exportTmx(OUT_DIR, "2", Maps.get("2", "F:\\mirall\\176client\\Map\\2.map"));
//		exportTmx(OUT_DIR, "3", Maps.get("3", "F:\\mirall\\176client\\Map\\3.map"));
//		exportTmx(OUT_DIR, "bsr02", Maps.get("bsr02", "D:\10-mir2\client\Map\\bsr02.map"));
    }

}
