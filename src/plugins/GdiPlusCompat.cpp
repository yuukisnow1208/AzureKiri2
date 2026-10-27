// GdiPlus / layerExDraw / layerExSave 互換レイヤー（サブセット実装）
//
// M2 系（wamsoft 插件体系）游戏脚本会无条件访问全局 GdiPlus 成员
// （world.tjs: `if (typeof GdiPlus != "undefined")`），而 TJS2 的裸标识符
// typeof 对不存在的全局成员会直接抛
// "Member GdiPlus does not exist"，导致游戏无法启动。
//
// 本模块以 "gdiplus.dll" 模块名默认激活（见 InternalPlugins.cpp），提供：
//  - 全局 GdiPlus.Appearance 类（addBrush 等，对应 wamsoft layerExDraw）
//  - Layer.drawPolygon(appearance, points) 多边形填充（even-odd 扫描线）
//  - Layer.copyBlueToAlpha(srcLayer)       蓝通道 → 本层 alpha
//  - Layer.getCropRect()                   非透明区域包围盒（layerExSave）
//
// 与 GDI+ 原版的差异：无抗锯齿、纯色笔刷（取 addBrush 的第一支），
// 渐变/纹理笔刷退化为纯色。对 extend 裁剪（矩形区域）用途足够。
#define NCB_MODULE_NAME TJS_W("gdiplus.dll")

#include "ncbind/ncbind.hpp"
#include "LayerExBase.h"
#include "ScriptMgnIntf.h"
#include "tjsDictionary.h"

#include <vector>
#include <algorithm>
#include <cmath>

using namespace std;

//---------------------------------------------------------------------------
// GdiPlus.Appearance
//---------------------------------------------------------------------------

class Appearance
{
protected:
	std::vector<tjs_uint32> _brushes; // 0xAARRGGBB
public:
	Appearance() {}
	virtual ~Appearance() {}

	/** 笔刷追加（GDI+ SolidBrush / GradientBrush 统一按纯色处理，取 ARGB） */
	void addBrush(tjs_int color, tjs_int x, tjs_int y) {
		_brushes.push_back((tjs_uint32)color);
	}
	void clearBrush() { _brushes.clear(); }
	tjs_int brushCount() const { return (tjs_int)_brushes.size(); }
	tjs_int brushAt(tjs_int index) const {
		if (index < 0 || index >= (tjs_int)_brushes.size()) return -1;
		return (tjs_int)_brushes[index];
	}
	/** drawPolygon 使用的笔刷颜色（第一支；无则不透明黑） */
	tjs_uint32 mainColor() const {
		return _brushes.empty() ? 0xff000000u : _brushes[0];
	}
};

NCB_GET_INSTANCE_HOOK(Appearance)
{
	NCB_INSTANCE_GETTER(objthis) {
		ClassT* obj = GetNativeInstance(objthis);
		if (!obj) {
			obj = new ClassT();
			SetNativeInstance(objthis, obj);
		}
		return obj;
	}
	~NCB_GET_INSTANCE_HOOK_CLASS() {}
};

NCB_REGISTER_CLASS(Appearance)
{
	NCB_METHOD(addBrush);
	NCB_METHOD(clearBrush);
	NCB_METHOD(brushCount);
	NCB_METHOD(brushAt);
}

//---------------------------------------------------------------------------
// 共通ユーティリティ
//---------------------------------------------------------------------------

/** layer 画像情報（毎回プロパティから取得） */
struct LayerBits
{
	tjs_int width, height, pitch;
	unsigned char *buffer;

	bool fetch(iTJSDispatch2 *objthis) {
		tTJSVariant v;
		if (TJS_FAILED(objthis->PropGet(0, TJS_W("imageWidth"), NULL, &v, objthis))) return false;
		width = v;
		if (TJS_FAILED(objthis->PropGet(0, TJS_W("imageHeight"), NULL, &v, objthis))) return false;
		height = v;
		if (TJS_FAILED(objthis->PropGet(0, TJS_W("mainImageBufferPitch"), NULL, &v, objthis))) return false;
		pitch = v;
		if (TJS_FAILED(objthis->PropGet(0, TJS_W("mainImageBufferForWrite"), NULL, &v, objthis))) return false;
		buffer = (unsigned char *)(tjs_int64)v;
		return buffer != NULL && width > 0 && height > 0;
	}
	void redraw(iTJSDispatch2 *objthis) {
		tTJSVariant varsp[4] = { (tjs_int)0, (tjs_int)0, width, height };
		tTJSVariant *varsp2[4] = { varsp, varsp + 1, varsp + 2, varsp + 3 };
		objthis->FuncCall(0, TJS_W("update"), NULL, NULL, 4, varsp2, objthis);
	}
};

/** BGRA バッファに source-over 合成で 1px 塗る */
inline void blendPixel(unsigned char *px, tjs_uint32 argb)
{
	const tjs_uint32 sa = (argb >> 24) & 0xff;
	const tjs_uint32 sr = (argb >> 16) & 0xff;
	const tjs_uint32 sg = (argb >> 8) & 0xff;
	const tjs_uint32 sb = argb & 0xff;
	if (sa == 0) return;
	if (sa == 0xff) {
		px[0] = (unsigned char)sb; px[1] = (unsigned char)sg;
		px[2] = (unsigned char)sr; px[3] = 0xff;
		return;
	}
	const tjs_uint32 ia = 0xff - sa;
	const tjs_uint32 da = px[3], dr = px[2], dg = px[1], db = px[0];
	px[0] = (unsigned char)((sb * sa + db * ia) / 0xff);
	px[1] = (unsigned char)((sg * sa + dg * ia) / 0xff);
	px[2] = (unsigned char)((sr * sa + dr * ia) / 0xff);
	px[3] = (unsigned char)(sa + da * ia / 0xff);
}

/** variant（配列要素）から x/y を取り出す。%[x:,y:] にも [x,y] にも対応 */
static bool fetchPoint(const tTJSVariant &v, double &x, double &y)
{
	if (v.Type() != tvtObject) return false;
	iTJSDispatch2 *obj = v.AsObjectNoAddRef();
	if (!obj) return false;
	tTJSVariant vx, vy;
	// 辞書形式 %[x:,y:]
	if (TJS_SUCCEEDED(obj->PropGet(0, TJS_W("x"), NULL, &vx, obj)) &&
		TJS_SUCCEEDED(obj->PropGet(0, TJS_W("y"), NULL, &vy, obj))) {
		x = vx; y = vy;
		return true;
	}
	// 配列形式 [x,y]
	if (TJS_SUCCEEDED(obj->PropGetByNum(0, 0, &vx, obj)) &&
		TJS_SUCCEEDED(obj->PropGetByNum(0, 1, &vy, obj))) {
		x = vx; y = vy;
		return true;
	}
	return false;
}

//---------------------------------------------------------------------------
// Layer 拡張 (drawPolygon / copyBlueToAlpha / getCropRect)
//---------------------------------------------------------------------------

class LayerExDraw
{
protected:
	iTJSDispatch2 *objthis;
public:
	LayerExDraw() : objthis(NULL) {}
	LayerExDraw(iTJSDispatch2 *_objthis) : objthis(_objthis) {}
	virtual ~LayerExDraw() {}

	/**
	 * 多边形填充（GdiPlus.FillPolygon 相当、even-odd、无抗锯齿）
	 * @param appearance GdiPlus.Appearance 实例
	 * @param points     顶点数组（元素为 %[x:,y:] 或 [x,y]）
	 */
	static tjs_error TJS_INTF_METHOD drawPolygon(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		LayerExDraw *nativeInstance)
	{
		iTJSDispatch2 *objthis = nativeInstance->objthis;
		if (numparams < 2) return TJS_E_BADPARAMCOUNT;
		if (!objthis) return TJS_E_NATIVECLASSCRASH;

		// 筆刷色
		tjs_uint32 argb = 0xff000000u;
		{
			iTJSDispatch2 *app = param[0]->Type() == tvtObject ? param[0]->AsObjectNoAddRef() : NULL;
			fetchMainColor(app, argb);
		}

		// 顶点收集
		if (param[1]->Type() != tvtObject) return TJS_E_INVALIDPARAM;
		iTJSDispatch2 *points = param[1]->AsObjectNoAddRef();
		tTJSVariant vcount;
		if (TJS_FAILED(points->PropGet(0, TJS_W("count"), NULL, &vcount, points)))
			return TJS_E_INVALIDPARAM;
		tjs_int count = vcount;
		if (count < 2) return TJS_S_OK;

		std::vector<double> xs(count), ys(count);
		tjs_int valid = 0;
		for (tjs_int i = 0; i < count; i++) {
			tTJSVariant item;
			if (TJS_FAILED(points->PropGetByNum(0, i, &item, points))) return TJS_E_INVALIDPARAM;
			double x, y;
			if (!fetchPoint(item, x, y)) return TJS_E_INVALIDPARAM;
			xs[valid] = x; ys[valid] = y; valid++;
		}
		if (valid < 2) return TJS_S_OK;

		LayerBits bits;
		if (!bits.fetch(objthis)) return TJS_S_OK;

		// 扫描线 even-odd 填充
		double ymin = ys[0], ymax = ys[0];
		for (tjs_int i = 1; i < valid; i++) {
			if (ys[i] < ymin) ymin = ys[i];
			if (ys[i] > ymax) ymax = ys[i];
		}
		tjs_int iy0 = max((tjs_int)floor(ymin), 0);
		tjs_int iy1 = min((tjs_int)ceil(ymax), bits.height - 1);
		for (tjs_int py = iy0; py <= iy1; py++) {
			double sy = py + 0.5;
			// 求与多边形各边的交点
			static const int MAXX = 64;
			double xings[MAXX];
			int nx = 0;
			for (tjs_int i = 0; i < valid && nx < MAXX; i++) {
				tjs_int j = (i + 1) % valid;
				double y0 = ys[i], y1 = ys[j];
				if ((y0 <= sy && y1 > sy) || (y1 <= sy && y0 > sy)) {
					double t = (sy - y0) / (y1 - y0);
					xings[nx++] = xs[i] + t * (xs[j] - xs[i]);
				}
			}
			if (nx < 2) continue;
			sort(xings, xings + nx);
			unsigned char *line = bits.buffer + (tjs_int64)py * bits.pitch;
			for (int k = 0; k + 1 < nx; k += 2) {
				tjs_int x0 = max((tjs_int)ceil(xings[k] - 0.5), 0);
				tjs_int x1 = min((tjs_int)floor(xings[k+1] - 0.5), bits.width - 1);
				for (tjs_int px = x0; px <= x1; px++) {
					blendPixel(line + (tjs_int64)px * 4, argb);
				}
			}
		}
		bits.redraw(objthis);
		return TJS_S_OK;
	}

	/** 蓝通道 → alpha 拷贝：本层 a = src 层 b */
	static tjs_error TJS_INTF_METHOD copyBlueToAlpha(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		LayerExDraw *nativeInstance)
	{
		iTJSDispatch2 *objthis = nativeInstance->objthis;
		if (numparams < 1 || param[0]->Type() != tvtObject) return TJS_E_BADPARAMCOUNT;
		if (!objthis) return TJS_E_NATIVECLASSCRASH;

		LayerBits dst, src;
		if (!dst.fetch(objthis)) return TJS_S_OK;
		if (!src.fetch(param[0]->AsObjectNoAddRef())) return TJS_S_OK;

		tjs_int w = min(dst.width, src.width);
		tjs_int h = min(dst.height, src.height);
		for (tjs_int y = 0; y < h; y++) {
			unsigned char *dline = dst.buffer + (tjs_int64)y * dst.pitch;
			unsigned char *sline = src.buffer + (tjs_int64)y * src.pitch;
			for (tjs_int x = 0; x < w; x++) {
				dline[(tjs_int64)x * 4 + 3] = sline[(tjs_int64)x * 4 + 0]; // a = b
			}
		}
		dst.redraw(objthis);
		return TJS_S_OK;
	}

	/** 非透明（alpha>0）区域包围盒；全透明时返回 void */
	static tjs_error TJS_INTF_METHOD getCropRect(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		LayerExDraw *nativeInstance)
	{
		iTJSDispatch2 *objthis = nativeInstance->objthis;
		if (!objthis) return TJS_E_NATIVECLASSCRASH;
		LayerBits bits;
		if (!bits.fetch(objthis)) return TJS_S_OK;

		tjs_int left = bits.width, top = bits.height, right = -1, bottom = -1;
		for (tjs_int y = 0; y < bits.height; y++) {
			unsigned char *line = bits.buffer + (tjs_int64)y * bits.pitch;
			for (tjs_int x = 0; x < bits.width; x++) {
				if (line[(tjs_int64)x * 4 + 3] > 0) {
					if (x < left) left = x;
					if (x > right) right = x;
					if (y < top) top = y;
					if (y > bottom) bottom = y;
				}
			}
		}
		if (right < 0) return TJS_S_OK; // 全透明 → void

		if (result) {
			iTJSDispatch2 *dict = TJSCreateDictionaryObject();
			tTJSVariant vl(left), vt(top), vw(right - left + 1), vh(bottom - top + 1);
			dict->PropSet(TJS_MEMBERENSURE, TJS_W("left"),   NULL, &vl, dict);
			dict->PropSet(TJS_MEMBERENSURE, TJS_W("top"),    NULL, &vt, dict);
			dict->PropSet(TJS_MEMBERENSURE, TJS_W("width"),  NULL, &vw, dict);
			dict->PropSet(TJS_MEMBERENSURE, TJS_W("height"), NULL, &vh, dict);
			*result = tTJSVariant(dict, dict);
			dict->Release();
		}
		return TJS_S_OK;
	}

	/** appearance オブジェクトから筆刷色を取得（メソッド経由、実装非依存） */
	static bool fetchMainColor(iTJSDispatch2 *app, tjs_uint32 &color) {
		if (!app) return false;
		tTJSVariant v;
		if (TJS_FAILED(app->FuncCall(0, TJS_W("brushCount"), NULL, &v, 0, NULL, app)))
			return false;
		if ((tjs_int)v <= 0) return false;
		if (TJS_FAILED(app->FuncCall(0, TJS_W("brushAt"), NULL, &v, 0, NULL, app)))
			return false;
		color = (tjs_uint32)(tjs_int)v;
		return true;
	}
};

NCB_GET_INSTANCE_HOOK(LayerExDraw)
{
	NCB_INSTANCE_GETTER(objthis) {
		ClassT* obj = GetNativeInstance(objthis);
		if (!obj) {
			obj = new ClassT(objthis);
			SetNativeInstance(objthis, obj);
		}
		return obj;
	}
	~NCB_GET_INSTANCE_HOOK_CLASS() {}
};

NCB_ATTACH_CLASS_WITH_HOOK(LayerExDraw, Layer)
{
	NCB_METHOD_RAW_CALLBACK(TJS_W("drawPolygon"),      LayerExDraw::drawPolygon,      0);
	NCB_METHOD_RAW_CALLBACK(TJS_W("copyBlueToAlpha"),  LayerExDraw::copyBlueToAlpha,  0);
	NCB_METHOD_RAW_CALLBACK(TJS_W("getCropRect"),      LayerExDraw::getCropRect,      0);
}

//---------------------------------------------------------------------------
// GdiPlus 全局对象注册
//---------------------------------------------------------------------------

/** global.GdiPlus = %["Appearance" => Appearance类] */
void InitGdiPlusGlobal()
{
	iTJSDispatch2 *global = TVPGetScriptDispatch();
	if (!global) return;
	tTJSVariant varApp;
	if (TJS_FAILED(global->PropGet(0, TJS_W("Appearance"), NULL, &varApp, global))) {
		global->Release();
		return;
	}
	iTJSDispatch2 *dict = TJSCreateDictionaryObject();
	if (dict) {
		tTJSVariant entry(varApp);
		dict->PropSet(TJS_MEMBERENSURE, TJS_W("Appearance"), NULL, &entry, dict);
		tTJSVariant dictv(dict, dict);
		global->PropSet(TJS_MEMBERENSURE, TJS_W("GdiPlus"), NULL, &dictv, global);
		dict->Release();
	}
	global->Release();
}

NCB_POST_REGIST_CALLBACK(InitGdiPlusGlobal);
