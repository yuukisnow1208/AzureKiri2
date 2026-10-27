// WindowEx 互換レイヤー（wamsoft windowEx.dll のサブセット実装）
//
// krkrz 時代の KAG3 スクリプトで書かれたゲームは、メッセージフック系 API
// （Window.getNotificationNum / Window.setMessageHook 等）を無条件に呼ぶ
// 場合がある（custom.tjs:2829 など）。zeas2 版 Kirikiroid2 1.3.9 は
// windowEx.dll を内蔵しているが、本エンジンは未実装のため
// "Member "getNotificationNum" does not exist" で起動できない。
//
// 注意：Window.menu / MenuItem はエンジンコアに既に存在する
// （src/core/visual/MenuItemIntf.cpp の tTJSNC_MenuItem）ので、ここでは
// 絶対に再登録しないこと。ネイティブ実装と二重登録すると、
// menu.add() がネイティブ AddChild にネイティブ实例でない物を渡して
// SIGSEGV する（実測済み）。
//
// Android 上では Win32 メッセージが存在しないため、フック登録は記録のみで
// 配信は行わない（1.3.9 と同じくデスクトップ向けイベントは不活性）。
// 提供メンバ（ゲームが必要とする面のみ）：
//   Window（クラス）: getNotificationNum / getNotificationName / ncht* 定数 /
//                      exSystemMenu プロパティ
//   Window（インスタンス）: setMessageHook / registerExEvent / ncHitTest /
//                            getClientRect / getWindowRect / getNormalRect /
//                            focusMenuByKey
//   System（クラス）: getCursorPos / setCursorPos / getSystemMetrics /
//                      readEnvValue / expandEnvString
#define NCB_MODULE_NAME TJS_W("windowEx.dll")

#include "ncbind/ncbind.hpp"

//---------------------------------------------------------------------------
// Win32 メッセージ名 → 番号テーブル（1.3.9 同梱 windowEx と同一の名前集合、
// 番号は Win32 標準値。フック配信がないため値は一意である事のみが重要）
//---------------------------------------------------------------------------
struct tNotificationEntry
{
	const tjs_char *name;
	tjs_int num;
};

static const tNotificationEntry Notifications[] = {
	{ TJS_W("NULL"), 0x0000 },
	{ TJS_W("CREATE"), 0x0001 },
	{ TJS_W("DESTROY"), 0x0002 },
	{ TJS_W("MOVE"), 0x0003 },
	{ TJS_W("SIZE"), 0x0005 },
	{ TJS_W("ACTIVATE"), 0x0006 },
	{ TJS_W("SETFOCUS"), 0x0007 },
	{ TJS_W("KILLFOCUS"), 0x0008 },
	{ TJS_W("ENABLE"), 0x000A },
	{ TJS_W("SETREDRAW"), 0x000B },
	{ TJS_W("SETTEXT"), 0x000C },
	{ TJS_W("GETTEXT"), 0x000D },
	{ TJS_W("GETTEXTLENGTH"), 0x000E },
	{ TJS_W("PAINT"), 0x000F },
	{ TJS_W("CLOSE"), 0x0010 },
	{ TJS_W("QUERYENDSESSION"), 0x0011 },
	{ TJS_W("QUIT"), 0x0012 },
	{ TJS_W("QUERYOPEN"), 0x0013 },
	{ TJS_W("ERASEBKGND"), 0x0014 },
	{ TJS_W("SYSCOLORCHANGE"), 0x0015 },
	{ TJS_W("SHOWWINDOW"), 0x0018 },
	{ TJS_W("CTLCOLOR"), 0x0019 },
	{ TJS_W("WININICHANGE"), 0x001A },
	{ TJS_W("SETTINGCHANGE"), 0x001A },
	{ TJS_W("DEVMODECHANGE"), 0x001B },
	{ TJS_W("ACTIVATEAPP"), 0x001C },
	{ TJS_W("FONTCHANGE"), 0x001D },
	{ TJS_W("TIMECHANGE"), 0x001E },
	{ TJS_W("CANCELMODE"), 0x001F },
	{ TJS_W("SETCURSOR"), 0x0020 },
	{ TJS_W("MOUSEACTIVATE"), 0x0021 },
	{ TJS_W("CHILDACTIVATE"), 0x0022 },
	{ TJS_W("QUEUESYNC"), 0x0023 },
	{ TJS_W("GETMINMAXINFO"), 0x0024 },
	{ TJS_W("PAINTICON"), 0x0026 },
	{ TJS_W("ICONERASEBKGND"), 0x0027 },
	{ TJS_W("NEXTDLGCTL"), 0x0028 },
	{ TJS_W("SPOOLERSTATUS"), 0x002A },
	{ TJS_W("DRAWITEM"), 0x002B },
	{ TJS_W("MEASUREITEM"), 0x002C },
	{ TJS_W("DELETEITEM"), 0x002D },
	{ TJS_W("VKEYTOITEM"), 0x002E },
	{ TJS_W("CHARTOITEM"), 0x002F },
	{ TJS_W("SETFONT"), 0x0030 },
	{ TJS_W("GETFONT"), 0x0031 },
	{ TJS_W("SETHOTKEY"), 0x0032 },
	{ TJS_W("GETHOTKEY"), 0x0033 },
	{ TJS_W("QUERYDRAGICON"), 0x0037 },
	{ TJS_W("COMPAREITEM"), 0x0039 },
	{ TJS_W("COMPACTING"), 0x0041 },
	{ TJS_W("COMMNOTIFY"), 0x0044 },
	{ TJS_W("WINDOWPOSCHANGING"), 0x0046 },
	{ TJS_W("WINDOWPOSCHANGED"), 0x0047 },
	{ TJS_W("POWER"), 0x0048 },
	{ TJS_W("COPYDATA"), 0x004A },
	{ TJS_W("CANCELJOURNAL"), 0x004B },
	{ TJS_W("INPUTLANGCHANGEREQUEST"), 0x0050 },
	{ TJS_W("INPUTLANGCHANGE"), 0x0051 },
	{ TJS_W("TCARD"), 0x0052 },
	{ TJS_W("HELP"), 0x0053 },
	{ TJS_W("USERCHANGED"), 0x0054 },
	{ TJS_W("NOTIFYFORMAT"), 0x0055 },
	{ TJS_W("CONTEXTMENU"), 0x007B },
	{ TJS_W("STYLECHANGING"), 0x007C },
	{ TJS_W("STYLECHANGED"), 0x007D },
	{ TJS_W("DISPLAYCHANGE"), 0x007E },
	{ TJS_W("GETICON"), 0x007F },
	{ TJS_W("SETICON"), 0x0080 },
	{ TJS_W("NCCREATE"), 0x0081 },
	{ TJS_W("NCDESTROY"), 0x0082 },
	{ TJS_W("NCCALCSIZE"), 0x0083 },
	{ TJS_W("NCHITTEST"), 0x0084 },
	{ TJS_W("NCPAINT"), 0x0085 },
	{ TJS_W("NCACTIVATE"), 0x0086 },
	{ TJS_W("GETDLGCODE"), 0x0087 },
	{ TJS_W("SYNCPAINT"), 0x0088 },
	{ TJS_W("NCMOUSEMOVE"), 0x00A0 },
	{ TJS_W("NCLBUTTONDOWN"), 0x00A1 },
	{ TJS_W("NCLBUTTONUP"), 0x00A2 },
	{ TJS_W("NCLBUTTONDBLCLK"), 0x00A3 },
	{ TJS_W("NCRBUTTONDOWN"), 0x00A4 },
	{ TJS_W("NCRBUTTONUP"), 0x00A5 },
	{ TJS_W("NCRBUTTONDBLCLK"), 0x00A6 },
	{ TJS_W("NCMBUTTONDOWN"), 0x00A7 },
	{ TJS_W("NCMBUTTONUP"), 0x00A8 },
	{ TJS_W("NCMBUTTONDBLCLK"), 0x00A9 },
	{ TJS_W("NCXBUTTONDOWN"), 0x00AB },
	{ TJS_W("NCXBUTTONUP"), 0x00AC },
	{ TJS_W("NCXBUTTONDBLCLK"), 0x00AD },
	{ TJS_W("INPUT_DEVICE_CHANGE"), 0x00FE },
	{ TJS_W("INPUT"), 0x00FF },
	{ TJS_W("KEYFIRST"), 0x0100 },
	{ TJS_W("KEYDOWN"), 0x0100 },
	{ TJS_W("KEYUP"), 0x0101 },
	{ TJS_W("CHAR"), 0x0102 },
	{ TJS_W("DEADCHAR"), 0x0103 },
	{ TJS_W("SYSKEYDOWN"), 0x0104 },
	{ TJS_W("SYSKEYUP"), 0x0105 },
	{ TJS_W("SYSCHAR"), 0x0106 },
	{ TJS_W("SYSDEADCHAR"), 0x0107 },
	{ TJS_W("UNICHAR"), 0x0109 },
	{ TJS_W("IME_STARTCOMPOSITION"), 0x010D },
	{ TJS_W("IME_ENDCOMPOSITION"), 0x010E },
	{ TJS_W("IME_COMPOSITION"), 0x010F },
	{ TJS_W("IME_KEYLAST"), 0x010F },
	{ TJS_W("INITDIALOG"), 0x0110 },
	{ TJS_W("SYSCOMMAND"), 0x0112 },
	{ TJS_W("TIMER"), 0x0113 },
	{ TJS_W("INITMENU"), 0x0116 },
	{ TJS_W("MENUSELECT"), 0x011F },
	{ TJS_W("MENUCHAR"), 0x0120 },
	{ TJS_W("ENTERIDLE"), 0x0121 },
	{ TJS_W("MENURBUTTONUP"), 0x0122 },
	{ TJS_W("MENUDRAG"), 0x0123 },
	{ TJS_W("MENUGETOBJECT"), 0x0124 },
	{ TJS_W("UNINITMENUPOPUP"), 0x0125 },
	{ TJS_W("MENUCOMMAND"), 0x0126 },
	{ TJS_W("CHANGEUISTATE"), 0x0127 },
	{ TJS_W("UPDATEUISTATE"), 0x0128 },
	{ TJS_W("QUERYUISTATE"), 0x0129 },
	{ TJS_W("CTLCOLORMSGBOX"), 0x0132 },
	{ TJS_W("CTLCOLOREDIT"), 0x0133 },
	{ TJS_W("CTLCOLORLISTBOX"), 0x0134 },
	{ TJS_W("CTLCOLORBTN"), 0x0135 },
	{ TJS_W("CTLCOLORDLG"), 0x0136 },
	{ TJS_W("CTLCOLORSCROLLBAR"), 0x0137 },
	{ TJS_W("CTLCOLORSTATIC"), 0x0138 },
	{ TJS_W("MOUSEFIRST"), 0x0200 },
	{ TJS_W("MOUSEMOVE"), 0x0200 },
	{ TJS_W("MOUSEWHEEL"), 0x020A },
	{ TJS_W("MOUSEHWHEEL"), 0x020E },
	{ TJS_W("MOUSELAST"), 0x020E },
	{ TJS_W("PARENTNOTIFY"), 0x0210 },
	{ TJS_W("ENTERMENULOOP"), 0x0211 },
	{ TJS_W("EXITMENULOOP"), 0x0212 },
	{ TJS_W("NEXTMENU"), 0x0213 },
	{ TJS_W("SIZING"), 0x0214 },
	{ TJS_W("CAPTURECHANGED"), 0x0215 },
	{ TJS_W("MOVING"), 0x0216 },
	{ TJS_W("POWERBROADCAST"), 0x0218 },
	{ TJS_W("DEVICECHANGE"), 0x0219 },
	{ TJS_W("MDICREATE"), 0x0220 },
	{ TJS_W("MDIDESTROY"), 0x0221 },
	{ TJS_W("MDIACTIVATE"), 0x0222 },
	{ TJS_W("MDIRESTORE"), 0x0223 },
	{ TJS_W("MDINEXT"), 0x0224 },
	{ TJS_W("MDIMAXIMIZE"), 0x0225 },
	{ TJS_W("MDITILE"), 0x0226 },
	{ TJS_W("MDICASCADE"), 0x0227 },
	{ TJS_W("MDIICONARRANGE"), 0x0228 },
	{ TJS_W("MDIGETACTIVE"), 0x0229 },
	{ TJS_W("MDISETMENU"), 0x0230 },
	{ TJS_W("ENTERSIZEMOVE"), 0x0231 },
	{ TJS_W("EXITSIZEMOVE"), 0x0232 },
	{ TJS_W("DROPFILES"), 0x0233 },
	{ TJS_W("MDIREFRESHMENU"), 0x0234 },
	{ TJS_W("IME_SETCONTEXT"), 0x0281 },
	{ TJS_W("IME_NOTIFY"), 0x0282 },
	{ TJS_W("IME_CONTROL"), 0x0283 },
	{ TJS_W("IME_COMPOSITIONFULL"), 0x0284 },
	{ TJS_W("IME_SELECT"), 0x0285 },
	{ TJS_W("IME_CHAR"), 0x0286 },
	{ TJS_W("IME_REQUEST"), 0x0288 },
	{ TJS_W("IME_KEYDOWN"), 0x0290 },
	{ TJS_W("IME_KEYUP"), 0x0291 },
	{ TJS_W("NCMOUSEHOVER"), 0x02A0 },
	{ TJS_W("NCMOUSELEAVE"), 0x02A2 },
	{ TJS_W("WTSSESSION_CHANGE"), 0x02B1 },
	{ TJS_W("TABLET_FIRST"), 0x05C0 },
	{ TJS_W("TABLET_LAST"), 0x05DF },
	{ TJS_W("CUT"), 0x0300 },
	{ TJS_W("COPY"), 0x0301 },
	{ TJS_W("PASTE"), 0x0302 },
	{ TJS_W("CLEAR"), 0x0303 },
	{ TJS_W("UNDO"), 0x0304 },
	{ TJS_W("RENDERFORMAT"), 0x0305 },
	{ TJS_W("RENDERALLFORMATS"), 0x0306 },
	{ TJS_W("DESTROYCLIPBOARD"), 0x0307 },
	{ TJS_W("DRAWCLIPBOARD"), 0x0308 },
	{ TJS_W("PAINTCLIPBOARD"), 0x0309 },
	{ TJS_W("VSCROLLCLIPBOARD"), 0x030A },
	{ TJS_W("SIZECLIPBOARD"), 0x030B },
	{ TJS_W("ASKCBFORMATNAME"), 0x030C },
	{ TJS_W("CHANGECBCHAIN"), 0x030D },
	{ TJS_W("HSCROLLCLIPBOARD"), 0x030E },
	{ TJS_W("QUERYNEWPALETTE"), 0x030F },
	{ TJS_W("PALETTEISCHANGING"), 0x0310 },
	{ TJS_W("PALETTECHANGED"), 0x0311 },
	{ TJS_W("PRINT"), 0x0317 },
	{ TJS_W("PRINTCLIENT"), 0x0318 },
	{ TJS_W("APPCOMMAND"), 0x0319 },
	{ TJS_W("THEMECHANGED"), 0x031A },
	{ TJS_W("CLIPBOARDUPDATE"), 0x031D },
	{ TJS_W("DWMCOMPOSITIONCHANGED"), 0x031E },
	{ TJS_W("DWMNCRENDERINGCHANGED"), 0x031F },
	{ TJS_W("DWMCOLORIZATIONCOLORCHANGED"), 0x0320 },
	{ TJS_W("DWMWINDOWMAXIMIZEDCHANGE"), 0x0321 },
	{ TJS_W("GETTITLEBARINFOEX"), 0x033F },
	{ TJS_W("HANDHELDFIRST"), 0x0358 },
	{ TJS_W("HANDHELDLAST"), 0x035F },
	{ TJS_W("AFXFIRST"), 0x0360 },
	{ TJS_W("AFXLAST"), 0x037F },
	{ TJS_W("PENWINFIRST"), 0x0380 },
	{ TJS_W("PENWINLAST"), 0x038F },
	{ NULL, 0 }
};

static bool LookupNotification(const ttstr &name, tjs_int &num)
{
	for (const tNotificationEntry *p = Notifications; p->name; p++) {
		if (name == p->name) { num = p->num; return true; }
	}
	return false;
}

static bool LookupNotificationName(tjs_int num, ttstr &name)
{
	for (const tNotificationEntry *p = Notifications; p->name; p++) {
		if (p->num == num) { name = p->name; return true; }
	}
	return false;
}

/** ncht* 定数（Win32 HT* 値） */
struct tHitTestEntry
{
	const tjs_char *name;
	tjs_int num;
};

static const tHitTestEntry HitTests[] = {
	{ TJS_W("nchtError"), -2 },
	{ TJS_W("nchtTransparent"), -1 },
	{ TJS_W("nchtNoWhere"), 0 },
	{ TJS_W("nchtClient"), 1 },
	{ TJS_W("nchtCaption"), 2 },
	{ TJS_W("nchtSysMenu"), 3 },
	{ TJS_W("nchtSize"), 4 },
	{ TJS_W("nchtGrowBox"), 4 },
	{ TJS_W("nchtMenu"), 5 },
	{ TJS_W("nchtHScroll"), 6 },
	{ TJS_W("nchtVScroll"), 7 },
	{ TJS_W("nchtMinButton"), 8 },
	{ TJS_W("nchtReduce"), 8 },
	{ TJS_W("nchtMaxButton"), 9 },
	{ TJS_W("nchtZoom"), 9 },
	{ TJS_W("nchtLeft"), 10 },
	{ TJS_W("nchtRight"), 11 },
	{ TJS_W("nchtTop"), 12 },
	{ TJS_W("nchtTopLeft"), 13 },
	{ TJS_W("nchtTopRight"), 14 },
	{ TJS_W("nchtBottom"), 15 },
	{ TJS_W("nchtBottomLeft"), 16 },
	{ TJS_W("nchtBottomRight"), 17 },
	{ TJS_W("nchtBorder"), 18 },
	{ NULL, 0 }
};

//---------------------------------------------------------------------------
// Window クラスへのアタッチ
//---------------------------------------------------------------------------
class WindowExCompat
{
public:
	// ---- クラスメソッド（Window.getNotificationNum(...) で呼ばれる）----

	/** メッセージ名（または番号）→ 番号。見つからない名前は 0 を返す */
	static tjs_error TJS_INTF_METHOD getNotificationNum(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (numparams < 1) return TJS_E_BADPARAMCOUNT;
		if (result) {
			if (param[0]->Type() == tvtString) {
				tjs_int num = 0;
				LookupNotification(*param[0], num);
				*result = num;
			} else {
				*result = (tjs_int)*param[0]; // 数値は素通し
			}
		}
		return TJS_S_OK;
	}

	/** 番号 → メッセージ名。未知は空文字列 */
	static tjs_error TJS_INTF_METHOD getNotificationName(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (numparams < 1) return TJS_E_BADPARAMCOUNT;
		if (result) {
			ttstr name;
			LookupNotificationName((tjs_int)*param[0], name);
			*result = name;
		}
		return TJS_S_OK;
	}

	// ---- インスタンスメソッド（Android では全て不活性スタブ）----

	/**
	 * メッセージフック設定（krkrz 相当）。
	 * setMessageHook(true, name_or_num) で登録 / setMessageHook(false) で全解除。
	 * フックされたメッセージは本来 onWindowsMessageHook(msg, wp, lp) で配信
	 * されるが、Android には Win32 メッセージが無いため配信は行わない。
	 */
	static tjs_error TJS_INTF_METHOD setMessageHook(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (numparams < 1) return TJS_E_BADPARAMCOUNT;
		if (result) *result = true;
		return TJS_S_OK;
	}

	/** 拡張イベント（onMove/onResizing/onNcMouseMove 等）有効化。配信なし */
	static tjs_error TJS_INTF_METHOD registerExEvent(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (result) *result = true;
		return TJS_S_OK;
	}

	/** 指定スクリーン座標のヒットテスト。全画面クライアント領域なので常に nchtClient */
	static tjs_error TJS_INTF_METHOD ncHitTest(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (result) *result = 1; // nchtClient
		return TJS_S_OK;
	}

	/** クライアント矩形（%[x:,y:,w:,h:]）。全画面 = (0,0,ウィンドウ幅,高さ) */
	static tjs_error TJS_INTF_METHOD getClientRect(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (result) {
			tTJSVariant vw, vh;
			tjs_int w = 0, h = 0;
			if (objthis) {
				if (TJS_SUCCEEDED(objthis->PropGet(0, TJS_W("width"), NULL, &vw, objthis)))
					w = vw;
				if (TJS_SUCCEEDED(objthis->PropGet(0, TJS_W("height"), NULL, &vh, objthis)))
					h = vh;
			}
			iTJSDispatch2 *dict = TJSCreateDictionaryObject();
			tTJSVariant vx((tjs_int)0), vy((tjs_int)0), vwid(w), vhei(h);
			dict->PropSet(TJS_MEMBERENSURE, TJS_W("x"), NULL, &vx, dict);
			dict->PropSet(TJS_MEMBERENSURE, TJS_W("y"), NULL, &vy, dict);
			dict->PropSet(TJS_MEMBERENSURE, TJS_W("w"), NULL, &vwid, dict);
			dict->PropSet(TJS_MEMBERENSURE, TJS_W("h"), NULL, &vhei, dict);
			*result = tTJSVariant(dict, dict);
			dict->Release();
		}
		return TJS_S_OK;
	}

	/** ウィンドウ矩形。全画面なので getClientRect と同じ */
	static tjs_error TJS_INTF_METHOD getWindowRect(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return getClientRect(result, numparams, param, objthis);
	}

	/** 通常状態の矩形。同上 */
	static tjs_error TJS_INTF_METHOD getNormalRect(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return getClientRect(result, numparams, param, objthis);
	}

	/** キーによるメニューフォーカス。不可 */
	static tjs_error TJS_INTF_METHOD focusMenuByKey(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	// ---- プロパティ（windowEx が Window に追加するもの）----

	/** Window.exSystemMenu：getter は void、setter は無視
	 *  （mainwindow.tjs の createMenuBarHideMenu が代入するだけで、
	 *    Android にシステムメニュー UI は存在しない） */
	static tjs_error TJS_INTF_METHOD getExSystemMenu(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	static tjs_error TJS_INTF_METHOD setExSystemMenu(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	/** Window.disableResize：getter は false、setter は無視
	 *  （mainwindow.tjs(3240) の windowResizeable setter が exEventEnabled 時に
	 *    代入する。リサイズ不可 UI は Android に無い） */
	static tjs_error TJS_INTF_METHOD getDisableResize(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (result) *result = false;
		return TJS_S_OK;
	}

	static tjs_error TJS_INTF_METHOD setDisableResize(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	/** Window.maximized / minimized：常に false */
	static tjs_error TJS_INTF_METHOD getMaximized(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (result) *result = false;
		return TJS_S_OK;
	}

	static tjs_error TJS_INTF_METHOD setMaximized(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	// ---- 不活性メソッド（デスクトップ向けウィンドウ操作）----

	/** 最小化。不可 */
	static tjs_error TJS_INTF_METHOD minimize(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	/** 最大化。不可 */
	static tjs_error TJS_INTF_METHOD maximize(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	/** 元サイズに戻す。不可 */
	static tjs_error TJS_INTF_METHOD showRestore(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	/** Z オーダ最前面へ。全画面単一ウィンドウなので何もしない */
	static tjs_error TJS_INTF_METHOD bringTo(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	/** Z オーダ最背面へ。同上 */
	static tjs_error TJS_INTF_METHOD sendToBack(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	/** 非クライアントマウスイベント有効化。NC イベントは配信されない */
	static tjs_error TJS_INTF_METHOD enableNCMouseEvent(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	/** ウィンドウアイコン再設定。不可 */
	static tjs_error TJS_INTF_METHOD resetWindowIcon(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	/** オーバーレイビットマップ設定。不可 */
	static tjs_error TJS_INTF_METHOD setOverlayBitmap(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}
};

NCB_ATTACH_CLASS(WindowExCompat, Window)
{
	RawCallback(TJS_W("getNotificationNum"),  &WindowExCompat::getNotificationNum,  TJS_STATICMEMBER);
	RawCallback(TJS_W("getNotificationName"), &WindowExCompat::getNotificationName, TJS_STATICMEMBER);
	RawCallback(TJS_W("setMessageHook"),      &WindowExCompat::setMessageHook,      0);
	RawCallback(TJS_W("registerExEvent"),     &WindowExCompat::registerExEvent,     0);
	RawCallback(TJS_W("ncHitTest"),           &WindowExCompat::ncHitTest,           0);
	RawCallback(TJS_W("getClientRect"),       &WindowExCompat::getClientRect,       0);
	RawCallback(TJS_W("getWindowRect"),       &WindowExCompat::getWindowRect,       0);
	RawCallback(TJS_W("getNormalRect"),       &WindowExCompat::getNormalRect,       0);
	RawCallback(TJS_W("focusMenuByKey"),      &WindowExCompat::focusMenuByKey,      0);
	// プロパティ（windowEx 由来）
	RawCallback(TJS_W("exSystemMenu"),        &WindowExCompat::getExSystemMenu,     &WindowExCompat::setExSystemMenu, 0);
	RawCallback(TJS_W("disableResize"),       &WindowExCompat::getDisableResize,    &WindowExCompat::setDisableResize, 0);
	RawCallback(TJS_W("maximized"),           &WindowExCompat::getMaximized,        &WindowExCompat::setMaximized, 0);
	RawCallback(TJS_W("minimized"),           &WindowExCompat::getMaximized,        &WindowExCompat::setMaximized, 0);
	// 不活性メソッド（デスクトップ向け操作）
	RawCallback(TJS_W("minimize"),            &WindowExCompat::minimize,            0);
	RawCallback(TJS_W("maximize"),            &WindowExCompat::maximize,            0);
	RawCallback(TJS_W("showRestore"),         &WindowExCompat::showRestore,         0);
	RawCallback(TJS_W("bringTo"),             &WindowExCompat::bringTo,             0);
	RawCallback(TJS_W("sendToBack"),          &WindowExCompat::sendToBack,          0);
	RawCallback(TJS_W("enableNCMouseEvent"),  &WindowExCompat::enableNCMouseEvent,  0);
	RawCallback(TJS_W("resetWindowIcon"),     &WindowExCompat::resetWindowIcon,     0);
	RawCallback(TJS_W("setOverlayBitmap"),    &WindowExCompat::setOverlayBitmap,    0);
	// ncht* 定数
	for (const tHitTestEntry *p = HitTests; p->name; p++) {
		Variant(p->name, p->num);
	}
}

//---------------------------------------------------------------------------
// System クラスへのアタッチ（1.3.9 内蔵 windowEx が提供していた分）
//---------------------------------------------------------------------------
class SystemExCompat
{
public:
	/** カーソル位置。タッチデバイスなので常に (0,0) */
	static tjs_error TJS_INTF_METHOD getCursorPos(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (result) {
			iTJSDispatch2 *dict = TJSCreateDictionaryObject();
			tTJSVariant vx((tjs_int)0), vy((tjs_int)0);
			dict->PropSet(TJS_MEMBERENSURE, TJS_W("x"), NULL, &vx, dict);
			dict->PropSet(TJS_MEMBERENSURE, TJS_W("y"), NULL, &vy, dict);
			*result = tTJSVariant(dict, dict);
			dict->Release();
		}
		return TJS_S_OK;
	}

	/** カーソル移動。不可 */
	static tjs_error TJS_INTF_METHOD setCursorPos(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		return TJS_S_OK;
	}

	/** GetSystemMetrics。名前（"CXSCREEN" 等）または番号を受け取る */
	static tjs_error TJS_INTF_METHOD getSystemMetrics(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (numparams < 1) return TJS_E_BADPARAMCOUNT;
		if (result) *result = 0;
		return TJS_S_OK;
	}

	/** 環境変数の読み取り。Android では基本的に存在しないので void */
	static tjs_error TJS_INTF_METHOD readEnvValue(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (numparams < 1) return TJS_E_BADPARAMCOUNT;
		if (result) result->Clear();
		return TJS_S_OK;
	}

	/** %VAR% 形式の環境変数展開（未知の変数はそのまま残す） */
	static tjs_error TJS_INTF_METHOD expandEnvString(
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (numparams < 1) return TJS_E_BADPARAMCOUNT;
		if (result) *result = *param[0];
		return TJS_S_OK;
	}
};

NCB_ATTACH_CLASS(SystemExCompat, System)
{
	RawCallback(TJS_W("getCursorPos"),     &SystemExCompat::getCursorPos,     TJS_STATICMEMBER);
	RawCallback(TJS_W("setCursorPos"),     &SystemExCompat::setCursorPos,     TJS_STATICMEMBER);
	RawCallback(TJS_W("getSystemMetrics"), &SystemExCompat::getSystemMetrics, TJS_STATICMEMBER);
	RawCallback(TJS_W("readEnvValue"),     &SystemExCompat::readEnvValue,     TJS_STATICMEMBER);
	RawCallback(TJS_W("expandEnvString"),  &SystemExCompat::expandEnvString,  TJS_STATICMEMBER);
}
