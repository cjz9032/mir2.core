package mir2.core;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;

import com.github.jootnet.mir2.core.Texture;
import com.github.jootnet.mir2.core.image.ImageInfo;
import com.github.jootnet.mir2.core.image.ImageLibraries;
import com.github.jootnet.mir2.core.map.Map;
import com.github.jootnet.mir2.core.map.MapTileInfo;
import com.github.jootnet.mir2.core.map.Maps;

// 一个地图切片工具，会将地图所有内容导出，并切片为图片
// 小地图导出需要一个配置文件
public class MapExtractor {
	static final int TILE_W = 48;
	static final int TILE_H = 32;
	
	static final int EXPAND_LEFT = 50;
	static final int EXPAND_TOP = 50;
	static final int EXPAND_RIGHT = 50;
	
	static final int MAX_ANI_FRAME = 16; // 尽量小！

	static final String MAP_DIR = "D:\\Program Files (x86)\\盛大网络\\热血传奇\\Map\\";
	static final String DATA_DIR = "D:\\Program Files (x86)\\盛大网络\\热血传奇\\Data\\";

	static final String OUTPUT_DIR = "C:\\Users\\云\\Desktop\\map\\";
	
	static final String SDO_DIR = "D:\\sdo";

	static class DoorInfo {
		public int x;
		public int y;
		public int opFlag;
		public DoorInfo(int x, int y, int opFlag) {
			this.x = x;
			this.y = y;
			this.opFlag = opFlag;
		}
	}
	static class AniInfo {
		public int x;
		public int y;
		public int tick;
		public int frame;
		public AniInfo(int x, int y, int tick, int frame) {
			this.x = x;
			this.y = y;
			this.tick = tick;
			this.frame = frame;
		}
	}
	
	public static void main(String[] args) throws IOException {
		String[] mapNos = new String[] { "0", "1", "2", "3" };
		for (String mapNo : mapNos) {
			File dir0 = new File(OUTPUT_DIR + mapNo + "\\layer_base");
			if (!dir0.exists())
				dir0.mkdirs();
			File dir1 = new File(OUTPUT_DIR + mapNo + "\\layer_obj");
			if (!dir1.exists())
				dir1.mkdir();
			File dir2 = new File(OUTPUT_DIR + mapNo + "\\layer_door_open");
			if(!dir2.exists())
				dir2.mkdir();
			File dir3 = new File(OUTPUT_DIR + mapNo + "\\layer_ani");
			if(!dir3.exists())
				dir3.mkdir();
			Map map = Maps.get(mapNo, MAP_DIR + mapNo + ".map");
			int step = 10;
			extractMMap(map, mapNo);
			buildLayerBase(map, mapNo, step);
			byte[][] objMask = new byte[map.getWidth()][map.getHeight()];
			buildLayerObj(map, mapNo, step, objMask);
			buildAni(map, mapNo, step);
			java.util.Map<Integer, List<DoorInfo>> doors = new HashMap<Integer, List<DoorInfo>>();
			buildLayerDoorOpen(map, mapNo, step, doors);
			buildInfoJson(map, mapNo, objMask, doors);
		}
	}

	private static void buildAni(Map map, String mapNo, int step) throws IOException {
		List<AniInfo> anis = new ArrayList<AniInfo>();
		for(int i = 0; i < MAX_ANI_FRAME; ++i)
			buildAniCore(map, mapNo, step, i, anis);
		File infoJson = new File(OUTPUT_DIR + mapNo + "\\info.ani.json");
		if (infoJson.exists())
			infoJson.delete();
		infoJson.createNewFile();
		StringBuilder sb = new StringBuilder();
		sb.append("var $$mapAni").append(mapNo).append(" = ");
		sb.append("[");
		if(!anis.isEmpty()) {
			for(int i = 0; i < anis.size(); ++i) {
				sb.append("{x:");
				sb.append(anis.get(i).x);
				sb.append(",y:");
				sb.append(anis.get(i).y);
				sb.append(",tick:");
				sb.append(anis.get(i).tick);
				sb.append(",frame:");
				sb.append(anis.get(i).frame);
				sb.append("}");
				if(i != anis.size() - 1)
					sb.append(",");
			}
		}
		sb.append("];");
		FileOutputStream fos = new FileOutputStream(infoJson);
		fos.write(sb.toString().getBytes());
		fos.close();
	}
	private static void buildAniCore(Map map, String mapNo, int step, int currentFrame, List<AniInfo> anis) throws IOException {
		int row = map.getHeight() / step;
		if (row * step < map.getHeight())
			row++;
		int col = map.getWidth() / step;
		if (col * step < map.getWidth())
			col++;
		for (int i = 0; i < row; ++i) {
			// 外部逐行
			int top = i * step;
			for (int j = 0; j < col; ++j) {
				// 外部逐列
				int left = j * step;
				Texture mapBaseTex = new Texture(new byte[TILE_W * step * TILE_H * step * 3], TILE_W * step,
						TILE_H * step);
				for (int h = top < EXPAND_TOP ? top : top - EXPAND_TOP; h < map.getHeight()
						&& h < top + step + EXPAND_TOP; ++h) {
					// 块儿内逐行
					for (int w = left < EXPAND_LEFT ? left : left - EXPAND_LEFT; w < map.getWidth()
							&& w < left + step + EXPAND_RIGHT; ++w) {
						// 块儿内逐列
						MapTileInfo mti = map.getTiles()[w][h];
						// 绘制左上角x
						int cpx = (w - left) * TILE_W;
						// 绘制左上角y
						int cpy = (h - top) * TILE_H;
						String objFileName = "Objects";
						if(mti.isHasAni()) {
							if(currentFrame >= mti.getAniFrame()) continue; // 它大爷的，盛大地图好多错误，为了容错，这里还不能return
							if (mti.getObjFileIdx() != 0)
								objFileName += mti.getObjFileIdx();
							if (ImageLibraries.get(objFileName, DATA_DIR + objFileName) == null)
								continue;
							Texture tex = ImageLibraries.get(objFileName, DATA_DIR + objFileName).tex(mti.getObjImgIdx() + currentFrame);
							ImageInfo ii = ImageLibraries.get(objFileName, DATA_DIR + objFileName).info(mti.getObjImgIdx() + currentFrame);
							if(mti.isAniBlendMode()) {
								mapBaseTex.blendAdd(tex, cpx + ii.getOffsetX(), cpy - tex.getHeight() + ii.getOffsetY() + TILE_H, 1);
							} else {
								mapBaseTex.blendNormalTransparent(tex, cpx + ii.getOffsetX(), cpy - tex.getHeight() + ii.getOffsetY() + TILE_H, 1, (byte) 0,
										(byte) 0, (byte) 0);
							}
						}
					}
				}
				boolean hasColor = false;
				for (int h1 = 0; h1 < TILE_H * step; ++h1) {
					for (int w1 = 0; w1 < TILE_W * step; ++w1) {
						byte[] rgb = mapBaseTex.getRGB(w1, h1);
						if (rgb[0] == 0 && rgb[1] == 0 && rgb[2] == 0)
							continue;
						hasColor = true;
						break;
					}
					if(hasColor) break;
				}
				if (!hasColor)
					continue;				
				BufferedImage bi_base = ImageIO.read(new File(OUTPUT_DIR + mapNo + "\\layer_base", String.format("%dx%d.png", left, top)));
				File f_obj = new File(OUTPUT_DIR + mapNo + "\\layer_obj", String.format("%dx%d.png", left, top));
				if(f_obj.exists()) {
					BufferedImage bi_obj = ImageIO.read(f_obj);
					Graphics2D g = bi_base.createGraphics();
					g.drawImage(bi_obj, 0, 0, null);
					g.dispose();
				}
				Texture t_base = new Texture(getMatrixRGB(bi_base), bi_base.getWidth(), bi_base.getHeight());
				t_base.blendAdd(mapBaseTex, 0, 0);
				BufferedImage bi = new BufferedImage(TILE_W * step, TILE_H * step, BufferedImage.TYPE_4BYTE_ABGR);
				byte[] _pixels = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
				for (int h1 = 0; h1 < TILE_H * step; ++h1) {
					for (int w1 = 0; w1 < TILE_W * step; ++w1) {
						byte[] rgb = t_base.getRGB(w1, h1);
						if (rgb[0] == 0 && rgb[1] == 0 && rgb[2] == 0)
							continue;
						int cw = left + w1 / TILE_W;
						int ch = top + h1 / TILE_H;
						Point ani = getCloseAni(map, new Point(cw, ch), 5);
						if(ani != null) {
							boolean got = false;
							for(AniInfo ai : anis) {
								if(ai.x == cw && ai.y == ch) {
									got = true;
									break;
								}
							}
							if(!got) {
								MapTileInfo mti = map.getTiles()[ani.x][ani.y];
								anis.add(new AniInfo(cw, ch, mti.getAniTick(), mti.getAniFrame()));
							}
						}
						int _idx = (w1 + h1 * TILE_W * step) * 4;
						_pixels[_idx + 3] = rgb[0];
						_pixels[_idx + 2] = rgb[1];
						_pixels[_idx + 1] = rgb[2];
						_pixels[_idx + 0] = -1;
					}
				}
				ImageIO.write(bi, "png",
						new File(OUTPUT_DIR + mapNo + "\\layer_ani", String.format("%dx%d_%d.png", left, top, currentFrame)));
			}
		}
	}
	private static Point getCloseAni(Map map, Point origin, int maxRadius) {
		if(map.getTiles()[origin.x][origin.y].isHasAni()) return origin;
		for(int i = 1; i < maxRadius; ++i) {
			Point ret = getCloseAniCore(map, origin, i);
			if(ret != null) return ret;
		}
		return null;
	}
	private static Point getCloseAniCore(Map map, Point origin, int radius) {
		// From right bottom to left up
		// RB
		Point ret = new Point(origin.x + radius, origin.y + radius);
		if(ret.x < map.getWidth() && ret.y < map.getHeight()) {
			if(map.getTiles()[ret.x][ret.y].isHasAni()) return ret;
		}
		// R
		ret = new Point(origin.x + radius, origin.y);
		if(ret.x < map.getWidth()) {
			if(map.getTiles()[ret.x][ret.y].isHasAni()) return ret;
		}
		// B
		ret = new Point(origin.x, origin.y + radius);
		if(ret.y < map.getHeight()) {
			if(map.getTiles()[ret.x][ret.y].isHasAni()) return ret;
		}
		// RT
		ret = new Point(origin.x + radius, origin.y - radius);
		if(ret.x < map.getWidth() && ret.y >= 0) {
			if(map.getTiles()[ret.x][ret.y].isHasAni()) return ret;
		}
		// LB
		ret = new Point(origin.x - radius, origin.y + radius);
		if(ret.x >= 0 && ret.y < map.getHeight()) {
			if(map.getTiles()[ret.x][ret.y].isHasAni()) return ret;
		}
		// U
		ret = new Point(origin.x, origin.y - radius);
		if(ret.y >= 0) {
			if(map.getTiles()[ret.x][ret.y].isHasAni()) return ret;
		}
		// L
		ret = new Point(origin.x - radius, origin.y);
		if(ret.x >= 0) {
			if(map.getTiles()[ret.x][ret.y].isHasAni()) return ret;
		}
		// LT
		ret = new Point(origin.x - radius, origin.y - radius);
		if(ret.x >= 0 && ret.y >= 0) {
			if(map.getTiles()[ret.x][ret.y].isHasAni()) return ret;
		}
		return null;
	}
	
	private static java.util.Map<String, Integer> MiniMaps;
	private static void extractMMap(Map map, String mapNo) throws IOException {
		if(MiniMaps == null) {
			MiniMaps = new HashMap<String, Integer>();
			File mmapFile = new File(SDO_DIR + "\\minimap.txt");
			if(mmapFile.exists()) {
				BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(mmapFile)));
				String line = null;
				while((line = br.readLine()) != null) {
					if(line.contains(";") || line.contains("#")) continue;
					String[] items = line.split("\\s+");
					if(items.length < 2) continue;
					try{
						MiniMaps.put(items[0], Integer.parseInt(items[1]));
					}
					catch(Exception ex)
					{
					}
				}
				br.close();
			}
		}
		if(MiniMaps.containsKey(mapNo)) {
			Texture mmap = ImageLibraries.get("mmap", DATA_DIR + "mmap").tex(MiniMaps.get(mapNo));
			if(mmap != null) {
				byte[] _pixels = mmap.getRGBs();
				// 将byte[]转为DataBufferByte用于后续创建BufferedImage对象
				DataBufferByte dataBuffer = new DataBufferByte(_pixels, _pixels.length);
				// sRGB色彩空间对象
				ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
				int[] nBits = { 8, 8, 8 };
				int[] bOffs = { 0, 1, 2 };
				ComponentColorModel colorModel = new ComponentColorModel(cs, nBits, false, false, Transparency.OPAQUE,
						DataBuffer.TYPE_BYTE);
				WritableRaster raster = Raster.createInterleavedRaster(dataBuffer, mmap.getWidth(), mmap.getHeight(),
						mmap.getWidth() * 3, 3, bOffs, null);
				BufferedImage bi = new BufferedImage(colorModel, raster, false, null);
				ImageIO.write(bi, "png",
						new File(OUTPUT_DIR + mapNo, "mmap.png"));
			}
		}
	}
	
	private static void buildLayerBase(Map map, String mapNo, int step) throws IOException {
		int row = map.getHeight() / step;
		if (row * step < map.getHeight())
			row++;
		int col = map.getWidth() / step;
		if (col * step < map.getWidth())
			col++;
		for (int i = 0; i < row; ++i) {
			// 外部逐行
			int top = i * step;
			for (int j = 0; j < col; ++j) {
				// 外部逐列
				int left = j * step;
				Texture mapBaseTex = new Texture(new byte[TILE_W * step * TILE_H * step * 3], TILE_W * step,
						TILE_H * step);
				for (int h = top; h < map.getHeight() && h < top + step; ++h) {
					// 块儿内逐行
					for (int w = left; w < map.getWidth() && w < left + step; ++w) {
						// 块儿内逐列
						MapTileInfo mti = map.getTiles()[w][h];
						// 绘制左上角x
						int cpx = (w - left) * TILE_W;
						// 绘制左上角y
						int cpy = (h - top) * TILE_H;
						if (mti.isHasBng()) {
							Texture tex = ImageLibraries.get("Tiles", DATA_DIR + "Tiles").tex(mti.getBngImgIdx());
							mapBaseTex.blendNormal(tex, cpx, cpy, 1);
						}
						if (mti.isHasMid()) {
							Texture tex = ImageLibraries.get("SmTiles", DATA_DIR + "SmTiles").tex(mti.getMidImgIdx());
							mapBaseTex.blendNormal(tex, cpx, cpy, 1);
						}
					}
				}
				byte[] _pixels = mapBaseTex.getRGBs();
				// 将byte[]转为DataBufferByte用于后续创建BufferedImage对象
				DataBufferByte dataBuffer = new DataBufferByte(_pixels, _pixels.length);
				// sRGB色彩空间对象
				ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
				int[] nBits = { 8, 8, 8 };
				int[] bOffs = { 0, 1, 2 };
				ComponentColorModel colorModel = new ComponentColorModel(cs, nBits, false, false, Transparency.OPAQUE,
						DataBuffer.TYPE_BYTE);
				WritableRaster raster = Raster.createInterleavedRaster(dataBuffer, TILE_W * step, TILE_H * step,
						TILE_W * step * 3, 3, bOffs, null);
				BufferedImage bi = new BufferedImage(colorModel, raster, false, null);
				ImageIO.write(bi, "png",
						new File(OUTPUT_DIR + mapNo + "\\layer_base", String.format("%dx%d.png", left, top)));
			}
		}
	}

	private static void buildLayerObj(Map map, String mapNo, int step, byte[][] objMask) throws IOException {
		int row = map.getHeight() / step;
		if (row * step < map.getHeight())
			row++;
		int col = map.getWidth() / step;
		if (col * step < map.getWidth())
			col++;
		for (int i = 0; i < row; ++i) {
			// 外部逐行
			int top = i * step;
			for (int j = 0; j < col; ++j) {
				// 外部逐列
				int left = j * step;
				Texture mapBaseTex = new Texture(new byte[TILE_W * step * TILE_H * step * 3], TILE_W * step,
						TILE_H * step);
				for (int h = top < EXPAND_TOP ? top : top - EXPAND_TOP; h < map.getHeight()
						&& h < top + step + EXPAND_TOP; ++h) {
					// 块儿内逐行
					for (int w = left < EXPAND_LEFT ? left : left - EXPAND_LEFT; w < map.getWidth()
							&& w < left + step + EXPAND_RIGHT; ++w) {
						// 块儿内逐列
						MapTileInfo mti = map.getTiles()[w][h];
						// 绘制左上角x
						int cpx = (w - left) * TILE_W;
						// 绘制左上角y
						int cpy = (h - top) * TILE_H;
						String objFileName = "Objects";
						if (mti.isHasObj()) {
							if (mti.getObjFileIdx() != 0)
								objFileName += mti.getObjFileIdx();
							if (ImageLibraries.get(objFileName, DATA_DIR + objFileName) == null)
								continue;
							Texture tex = ImageLibraries.get(objFileName, DATA_DIR + objFileName).tex(mti.getObjImgIdx());
							mapBaseTex.blendNormalTransparent(tex, cpx, cpy - tex.getHeight() + TILE_H, 1, (byte) 0, (byte) 0, (byte) 0);
						}
					}
				}
				BufferedImage bi = new BufferedImage(TILE_W * step, TILE_H * step, BufferedImage.TYPE_4BYTE_ABGR);
				byte[] _pixels = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
				boolean hasColor = false;
				for (int h1 = 0; h1 < TILE_H * step; ++h1) {
					for (int w1 = 0; w1 < TILE_W * step; ++w1) {
						byte[] rgb = mapBaseTex.getRGB(w1, h1);
						if (rgb[0] == 0 && rgb[1] == 0 && rgb[2] == 0)
							continue;
						if(objMask != null)
							objMask[left + w1 / TILE_W][top + h1 / TILE_H] = 1;
						int _idx = (w1 + h1 * TILE_W * step) * 4;
						hasColor = true;
						_pixels[_idx + 3] = rgb[0];
						_pixels[_idx + 2] = rgb[1];
						_pixels[_idx + 1] = rgb[2];
						_pixels[_idx + 0] = -1;
					}
				}
				if (!hasColor)
					continue;
				ImageIO.write(bi, "png", new File(OUTPUT_DIR + mapNo + "\\layer_obj", String.format("%dx%d.png", left, top)));
			}
		}
	}

	private static void buildLayerDoorOpen(Map map, String mapNo, int step, java.util.Map<Integer, List<DoorInfo>> doors) throws IOException {
		int row = map.getHeight() / step;
		if (row * step < map.getHeight())
			row++;
		int col = map.getWidth() / step;
		if (col * step < map.getWidth())
			col++;
		for (int i = 0; i < row; ++i) {
			// 外部逐行
			int top = i * step;
			for (int j = 0; j < col; ++j) {
				// 外部逐列
				int left = j * step;
				Texture mapBaseTex = new Texture(new byte[TILE_W * step * TILE_H * step * 3], TILE_W * step,
						TILE_H * step);
				for (int h = top < EXPAND_TOP ? top : top - EXPAND_TOP; h < map.getHeight()
						&& h < top + step + EXPAND_TOP; ++h) {
					// 块儿内逐行
					for (int w = left < EXPAND_LEFT ? left : left - EXPAND_LEFT; w < map.getWidth()
							&& w < left + step + EXPAND_RIGHT; ++w) {
						// 块儿内逐列
						MapTileInfo mti = map.getTiles()[w][h];
						// 绘制左上角x
						int cpx = (w - left) * TILE_W;
						// 绘制左上角y
						int cpy = (h - top) * TILE_H;
						String objFileName = "Objects";
						if (mti.isHasDoor()) {
							if (mti.getObjFileIdx() != 0)
								objFileName += mti.getObjFileIdx();
							if (ImageLibraries.get(objFileName, DATA_DIR + objFileName) == null)
								continue;
							Texture tex = ImageLibraries.get(objFileName, DATA_DIR + objFileName).tex(mti.getObjImgIdx() + mti.getDoorOffset());
							mapBaseTex.blendNormalTransparent(tex, cpx, cpy - tex.getHeight() + TILE_H, 1, (byte) 0, (byte) 0, (byte) 0);
						}
					}
				}
				BufferedImage bi = new BufferedImage(TILE_W * step, TILE_H * step, BufferedImage.TYPE_4BYTE_ABGR);
				byte[] _pixels = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
				boolean hasColor = false;
				for (int h1 = 0; h1 < TILE_H * step; ++h1) {
					for (int w1 = 0; w1 < TILE_W * step; ++w1) {
						byte[] rgb = mapBaseTex.getRGB(w1, h1);
						if (rgb[0] == 0 && rgb[1] == 0 && rgb[2] == 0)
							continue;
						if(doors != null) {
							int cw = left + w1 / TILE_W;
							int ch = top + h1 / TILE_H;
							MapTileInfo cmti = map.getTiles()[cw][ch];
							Integer ckey = null;
							for(Integer _key : doors.keySet()) {
								if(_key.byteValue() == cmti.getDoorIdx()) {
									ckey = _key;
									break;
								}
							}
							if(ckey == null) {
								ckey = new Integer(cmti.getDoorIdx());
								doors.put(ckey, new ArrayList<DoorInfo>());
							}
							List<DoorInfo> cps = doors.get(ckey);
							boolean alreadyPuts = false;
							for(DoorInfo cp : cps) {
								if(cp.x == cw && cp.y == ch) {
									alreadyPuts = true;
									break;
								}
							}
							if(!alreadyPuts)
								cps.add(new DoorInfo(cw, ch, cmti.isDoorCanOpen()?1:0));
						}
						int _idx = (w1 + h1 * TILE_W * step) * 4;
						hasColor = true;
						_pixels[_idx + 3] = rgb[0];
						_pixels[_idx + 2] = rgb[1];
						_pixels[_idx + 1] = rgb[2];
						_pixels[_idx + 0] = -1;
					}
				}
				if (!hasColor)
					continue;
				BufferedImage bi_obj = ImageIO.read(new File(OUTPUT_DIR + mapNo + "\\layer_obj", String.format("%dx%d.png", left, top)));
				Graphics2D g = bi_obj.createGraphics();
				g.drawImage(bi, 0, 0, null);
				g.dispose();
				ImageIO.write(bi_obj, "png",
						new File(OUTPUT_DIR + mapNo + "\\layer_door_open", String.format("%dx%d.png", left, top)));
			}
		}
	}
	
	private static void buildInfoJson(Map map, String mapNo, byte[][] objMask, java.util.Map<Integer, List<DoorInfo>> doors) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("var $$map").append(mapNo).append(" = ");
		sb.append("{");

		sb.append("id:");
		sb.append("\"");
		sb.append(mapNo);
		sb.append("\"");

		sb.append(",");

		sb.append("width:");
		sb.append(map.getWidth());

		sb.append(",");

		sb.append("height:");
		sb.append(map.getHeight());

		sb.append(",");

		if (objMask != null) {
			sb.append("obj:");
			String[] objs = new String[map.getHeight()];
			for (int h = 0; h < map.getHeight(); ++h) {
				StringBuilder sb_obj = new StringBuilder();
				sb_obj.append("\"");
				for (int w = 0; w < map.getWidth(); ++w) {
					sb_obj.append(objMask[w][h]);
				}
				sb_obj.append("\"");
				objs[h] = sb_obj.toString();
			}
			sb.append(Arrays.toString(objs));
			sb.append(",");
		}

		if (doors != null) {
			sb.append("door:");
			if(!doors.isEmpty()) {
				String[] _doors = new String[doors.size()];
				int _didx = 0;
				for(Integer didx : doors.keySet()) {
					StringBuilder sb_door = new StringBuilder();
					sb_door.append("{idx:");
					sb_door.append(didx.byteValue());
					sb_door.append(",points:[");
					List<DoorInfo> dps = doors.get(didx);
					if(!dps.isEmpty()) {
						for(int i = 0; i < dps.size(); ++i) {
							sb_door.append("{x:");
							sb_door.append(dps.get(i).x);
							sb_door.append(",y:");
							sb_door.append(dps.get(i).y);
							sb_door.append(",canOpen:");
							sb_door.append(dps.get(i).opFlag);
							sb_door.append("}");
							if(i != dps.size() - 1)
								sb_door.append(",");
						}
					}
					sb_door.append("]}");
					_doors[_didx++] = sb_door.toString();
				}
				sb.append(Arrays.toString(_doors));
			}
			sb.append(",");
		}

		sb.append("stand:");
		String[] stands = new String[map.getHeight()];
		for (int h = 0; h < map.getHeight(); ++h) {
			StringBuilder sb_stand = new StringBuilder();
			sb_stand.append("\"");
			for (int w = 0; w < map.getWidth(); ++w) {
				sb_stand.append(map.getTiles()[w][h].isCanWalk() ? '1' : '0');
			}
			sb_stand.append("\"");
			stands[h] = sb_stand.toString();
		}
		sb.append(Arrays.toString(stands));

		sb.append(",");

		sb.append("fly:");
		String[] flys = new String[map.getHeight()];
		for (int h = 0; h < map.getHeight(); ++h) {
			StringBuilder sb_fly = new StringBuilder();
			sb_fly.append("\"");
			for (int w = 0; w < map.getWidth(); ++w) {
				sb_fly.append(map.getTiles()[w][h].isCanFly() ? '1' : '0');
			}
			sb_fly.append("\"");
			flys[h] = sb_fly.toString();
		}
		sb.append(Arrays.toString(flys));

		sb.append("};");
		File infoJson = new File(OUTPUT_DIR + mapNo + "\\info.json");
		if (infoJson.exists())
			infoJson.delete();
		infoJson.createNewFile();
		FileOutputStream fos = new FileOutputStream(infoJson);
		fos.write(sb.toString().getBytes());
		fos.close();
	}
	
	/**
    * @param image
    * @param bandOffset 用于判断通道顺序
    * @return
    */
   private static boolean equalBandOffsetWith3Byte(BufferedImage image,int[] bandOffset){
       if(image.getType()==BufferedImage.TYPE_3BYTE_BGR){
           if(image.getData().getSampleModel() instanceof ComponentSampleModel){
               ComponentSampleModel sampleModel = (ComponentSampleModel)image.getData().getSampleModel();
               if(Arrays.equals(sampleModel.getBandOffsets(), bandOffset)){
                   return true;
               }
           }
       }
       return false;       
   }
   /**
    * 判断图像是否为RGB格式
    * @return 
    */
   public static boolean isRGB3Byte(BufferedImage image){
       return equalBandOffsetWith3Byte(image,new int[]{2, 1, 0});
   }
   /**
    * 对图像解码返回RGB格式矩阵数据
    * @param image
    * @return 
    */
   public static byte[] getMatrixRGB(BufferedImage image) {
       if(null==image)
           throw new NullPointerException();
       byte[] matrixRGB;
       if(isRGB3Byte(image)){
           matrixRGB= (byte[]) image.getData().getDataElements(0, 0, image.getWidth(), image.getHeight(), null);
       }else{
           // 转RGB格式
           BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(),  
                   BufferedImage.TYPE_3BYTE_BGR);
           new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_sRGB), null).filter(image, rgbImage);
           matrixRGB= (byte[]) rgbImage.getData().getDataElements(0, 0, image.getWidth(), image.getHeight(), null);
       } 
       return matrixRGB;
   }

}
