package mir2.core;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

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
 *
 */
public class TiledMapExporter {

	static String LINE_SEPARATOR = System.getProperty("line.separator");
	
	static void exportTsx(String dir, String ilName, ImageLibrary il) throws IOException {
		if(new File(dir, ilName + ".tsx").exists()) return; // XXX
		int maxWidth = 0;
		int maxHeight = 0;
		for(int i = 0; i < il.count(); ++i) {
			maxWidth = Math.max(maxWidth, il.info(i).getWidth());
			maxHeight = Math.max(maxHeight, il.info(i).getHeight());
		}
		File imgOutDir = new File(dir, ilName);
		if(!imgOutDir.exists())
			imgOutDir.mkdirs();
		// 做一个空图片
		BufferedImage emptyImg = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
		((DataBufferByte)emptyImg.getRaster().getDataBuffer()).getData()[0] = -1;
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
		
		for(int i = 0; i < il.count(); ++i) {
			tsxXml.append(" <tile id=\"").append(i).append("\">");
			tsxXml.append(LINE_SEPARATOR);
			Texture tex = il.tex(i);
			if(tex.empty()) {
				tsxXml.append("  <image width=\"1\" height=\"1\" source=\"").append(ilName).append("/empty.png\"/>");
			} else {
				ImageInfo ii = il.info(i);
				BufferedImage bi = new BufferedImage(ii.getWidth(), ii.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
				byte[] pixels = ((DataBufferByte)bi.getRaster().getDataBuffer()).getData();
				for(int j = 0; j < ii.getWidth() * ii.getHeight(); ++j) {
					pixels[j * 4 + 3] = tex.getRGBs()[j * 3];
					pixels[j * 4 + 2] = tex.getRGBs()[j * 3 + 1];
					pixels[j * 4 + 1] = tex.getRGBs()[j * 3 + 2];
					if(pixels[j * 4 + 3] != 0 || pixels[j * 4 + 1] != 0 || pixels[j * 4 + 2] != 0)
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
		if(new File(dir, mapName + ".tsx").exists()) return; // XXX
		// 看用到了哪些Obj
		int[] objGIDOffsets = new int[100];
		int objUseCount = 2; // 因为有SmTiles和Tiles
		for(int h = 0; h < map.getHeight(); ++h) {
			for(int w = 0; w < map.getWidth(); ++w) {
				MapTileInfo mti = map.getTiles()[w][h];
				if(mti.isHasObj()) {
					if(objGIDOffsets[mti.getObjFileIdx()] == 0) {
						objGIDOffsets[mti.getObjFileIdx()] = objUseCount++ * 32767 + 1;
					}
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
		tmxXml.append(" <tileset firstgid=\"1\" source=\"Tiles.tsx\"/>");
		tmxXml.append(LINE_SEPARATOR);
		tmxXml.append(" <tileset firstgid=\"32768\" source=\"SmTiles.tsx\"/>"); // 预设每个TSX有32767张图，浪费空间我不怕
		tmxXml.append(LINE_SEPARATOR);
		for(int i = 0; i < 100; ++i) {
			if(objGIDOffsets[i] != 0) {
				tmxXml.append(" <tileset firstgid=\"")
					.append(objGIDOffsets[i])
					.append("\" source=\"Objects");
				if(i != 0)
					tmxXml.append(i);
				tmxXml.append(".tsx\"/>");
				tmxXml.append(LINE_SEPARATOR);
			}
		}
		
		tmxXml.append(" <layer name=\"base\" width=\"")
			.append(map.getWidth())
			.append("\" height=\"")
			.append(map.getHeight())
			.append("\">");
		tmxXml.append(LINE_SEPARATOR);
		tmxXml.append("  <data encoding=\"csv\">");
		tmxXml.append(LINE_SEPARATOR);
		for(int w = 0; w < map.getWidth(); ++w) {
			tmxXml.append("0,"); // 第一行木有大砖块
		}
		tmxXml.append(LINE_SEPARATOR);
		for(int h = 1; h < map.getHeight(); ++h) {
			for(int w = 0; w < map.getWidth(); ++w) {
				MapTileInfo mti = map.getTiles()[w][h - 1];
				if(mti.isHasBng()) {
					tmxXml.append(mti.getBngImgIdx() + 1);
				} else {
					tmxXml.append("0");
				}
				if(w != map.getWidth() - 1 || h != map.getHeight() - 1)
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
		for(int h = 0; h < map.getHeight(); ++h) {
			for(int w = 0; w < map.getWidth(); ++w) {
				MapTileInfo mti = map.getTiles()[w][h];
				if(mti.isHasMid()) {
					tmxXml.append(mti.getMidImgIdx() + 32768);
				} else {
					tmxXml.append("0");
				}
				if(w != map.getWidth() - 1 || h != map.getHeight() - 1)
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
		for(int h = 0; h < map.getHeight(); ++h) {
			for(int w = 0; w < map.getWidth(); ++w) {
				MapTileInfo mti = map.getTiles()[w][h];
				if(mti.isHasObj()) {
					tmxXml.append(mti.getObjImgIdx() + objGIDOffsets[mti.getObjFileIdx()]);
				} else {
					tmxXml.append("0");
				}
				if(w != map.getWidth() - 1 || h != map.getHeight() - 1)
					tmxXml.append(",");
			}
			tmxXml.append(LINE_SEPARATOR);
		}
		tmxXml.append("  </data>");
		tmxXml.append(LINE_SEPARATOR);
		tmxXml.append(" </layer>");
		
		tmxXml.append(LINE_SEPARATOR);
		tmxXml.append("</map>");
		Files.write(new File(dir, mapName + ".tmx").toPath(), tmxXml.toString().getBytes(), StandardOpenOption.CREATE);
	}
	
	public static void main(String[] args) throws IOException {
		String OUT_DIR = "C:\\Users\\云\\Desktop\\M2";
		String DATA_DIR = "D:\\Program Files (x86)\\盛大网络\\热血传奇\\Data\\";
		
		ImageLibrary il_tiles = ImageLibraries.get("Tiles", DATA_DIR + "Tiles");
		exportTsx(OUT_DIR, "Tiles", il_tiles);
		ImageLibrary il_smTiles = ImageLibraries.get("SmTiles", DATA_DIR + "SmTiles");
		exportTsx(OUT_DIR, "SmTiles", il_smTiles);
		for(int i = 0; i < 100; ++i) {
			String ilName = "Objects";
			if(i != 0)
				ilName += i;
			ImageLibrary il_obj = ImageLibraries.get(ilName, DATA_DIR + ilName);
			if(il_obj != null)
				exportTsx(OUT_DIR, ilName, il_obj);
		}
		
		exportTmx(OUT_DIR, "0", Maps.get("0", "D:\\Program Files (x86)\\盛大网络\\热血传奇\\Map\\0.map"));
		exportTmx(OUT_DIR, "1", Maps.get("1", "D:\\Program Files (x86)\\盛大网络\\热血传奇\\Map\\1.map"));
		exportTmx(OUT_DIR, "2", Maps.get("2", "D:\\Program Files (x86)\\盛大网络\\热血传奇\\Map\\2.map"));
		exportTmx(OUT_DIR, "3", Maps.get("3", "D:\\Program Files (x86)\\盛大网络\\热血传奇\\Map\\3.map"));
	}

}
