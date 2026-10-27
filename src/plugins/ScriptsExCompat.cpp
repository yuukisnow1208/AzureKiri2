// ScriptsEx 互換レイヤー（wamsoft scriptsEx のサブセット移植）
//
// M2 系游戏脚本大量使用 Scripts.getObjectKeys / foreach / clone 等
// wamsoft scriptsEx 插件提供的 Scripts 类静态方法；引擎未提供时
// 直接抛 "Member xxx does not exist" 无法启动。
// 这里移植游戏实际用到的成员（与上游 wamsoft/scriptsEx 行为一致）：
//   getObjectKeys / getObjectCount / foreach / clone /
//   equalStruct / equalStructNumericLoose / getMD5HashString /
//   getObjectContext / isNullContext
#define NCB_MODULE_NAME TJS_W("scriptsEx.dll")

#include "ncbind/ncbind.hpp"
#include "PluginImpl.h"

#include <vector>
#include <algorithm>

//---------------------------------------------------------------------------

static tjs_uint32 countHint = 0;

// 前置声明（Caller 类内部相互引用）
static tTJSVariant ScriptsExClone(tTJSVariant obj);
static bool ScriptsExEqualStruct(tTJSVariant v1, tTJSVariant v2);

/** 辞書のキー一覧取得用 EnumMembers caller */
class DictMemberGetCaller : public tTJSDispatch
{
public:
	iTJSDispatch2 *array;
	DictMemberGetCaller(iTJSDispatch2 *array) : array(array) {}
	virtual tjs_error TJS_INTF_METHOD FuncCall(
		tjs_uint32 flag, const tjs_char *membername, tjs_uint32 *hint,
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (numparams > 1) {
			tTVInteger f = param[1]->AsInteger();
			static tjs_uint addHint = 0;
			if (!(f & TJS_HIDDENMEMBER)) {
				array->FuncCall(0, TJS_W("add"), &addHint, 0, 1, &param[0], array);
			}
		}
		if (result) *result = true;
		return TJS_S_OK;
	}
};

/** 辞書の要素を全cloneするCaller */
class DictMemberCloneCaller : public tTJSDispatch
{
public:
	iTJSDispatch2 *dict;
	DictMemberCloneCaller(iTJSDispatch2 *dict) : dict(dict) {}
	virtual tjs_error TJS_INTF_METHOD FuncCall(
		tjs_uint32 flag, const tjs_char *membername, tjs_uint32 *hint,
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		tTJSVariant value = ScriptsExClone(*param[2]);
		dict->PropSet(TJS_MEMBERENSURE | (tjs_int)*param[1], param[0]->GetString(), 0, &value, dict);
		if (result) *result = true;
		return TJS_S_OK;
	}
};

/** 辞書を巡回するcaller */
class DictIterateCaller : public tTJSDispatch
{
public:
	iTJSDispatch2 *func;
	iTJSDispatch2 *functhis;
	tTJSVariant **paramList;
	tjs_int paramCount;
	tTJSVariant breakResult;

	DictIterateCaller(iTJSDispatch2 *func, iTJSDispatch2 *functhis,
					  tTJSVariant **_paramList, tjs_int _paramCount)
		: func(func), functhis(functhis), paramList(_paramList), paramCount(_paramCount) {}

	virtual tjs_error TJS_INTF_METHOD FuncCall(
		tjs_uint32 flag, const tjs_char *membername, tjs_uint32 *hint,
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		breakResult.Clear();
		if (numparams > 1) {
			if ((tjs_int)*param[1] != TJS_HIDDENMEMBER) {
				paramList[0] = param[0];
				paramList[1] = param[2];
				(void)func->FuncCall(0, NULL, NULL, &breakResult, paramCount, paramList, functhis);
			}
		}
		if (result) *result = breakResult.Type() == tvtVoid;
		return TJS_S_OK;
	}
};

/** 辞書の要素を全比較するCaller */
class DictMemberCompareCaller : public tTJSDispatch
{
public:
	tTJSVariantClosure &target;
	bool match;

	DictMemberCompareCaller(tTJSVariantClosure &_target)
		: target(_target), match(true) {}

	virtual tjs_error TJS_INTF_METHOD FuncCall(
		tjs_uint32 flag, const tjs_char *membername, tjs_uint32 *hint,
		tTJSVariant *result, tjs_int numparams, tTJSVariant **param,
		iTJSDispatch2 *objthis) {
		if (result) *result = true;
		if (numparams > 1) {
			if ((tjs_int)*param[1] != TJS_HIDDENMEMBER) {
				const tjs_char *key = param[0]->GetString();
				tTJSVariant value = *param[2];
				tTJSVariant targetValue;
				if (target.PropGet(TJS_MEMBERMUSTEXIST, key, NULL, &targetValue, NULL) == TJS_S_OK) {
					match = match && ScriptsExEqualStruct(value, targetValue);
					if (result) *result = match;
				} else {
					match = false;
					if (result) *result = match;
				}
			}
		}
		return TJS_S_OK;
	}
};

//---------------------------------------------------------------------------
// コア実装
//---------------------------------------------------------------------------

static void _getKeys(tTJSVariant *result, tTJSVariant &obj)
{
	if (result) {
		iTJSDispatch2 *array = TJSCreateArrayObject();
		DictMemberGetCaller *caller = new DictMemberGetCaller(array);
		tTJSVariantClosure closure(caller);
		obj.AsObjectClosureNoAddRef().EnumMembers(TJS_IGNOREPROP | TJS_ENUM_NO_VALUE, &closure, NULL);
		caller->Release();
		static tjs_uint sortHint = 0;
		array->FuncCall(0, TJS_W("sort"), &sortHint, 0, 0, 0, array);
		*result = tTJSVariant(array, array);
		array->Release();
	}
}

static tTJSVariant ScriptsExClone(tTJSVariant obj)
{
	if (obj.Type() == tvtObject) {
		tTJSVariantClosure &o1 = obj.AsObjectClosureNoAddRef();
		if (!o1.Object) return obj;

		// Array
		if (o1.IsInstanceOf(0, NULL, NULL, TJS_W("Array"), NULL) == TJS_S_TRUE) {
			iTJSDispatch2 *array = TJSCreateArrayObject();
			tTJSVariant o1Count;
			(void)o1.PropGet(0, TJS_W("count"), &countHint, &o1Count, NULL);
			tjs_int count = o1Count;
			tTJSVariant val;
			tTJSVariant *args[] = { &val };
			for (tjs_int i = 0; i < count; i++) {
				(void)o1.PropGetByNum(TJS_IGNOREPROP, i, &val, NULL);
				val = ScriptsExClone(val);
				static tjs_uint addHint = 0;
				(void)array->FuncCall(0, TJS_W("add"), &addHint, 0, 1, args, array);
			}
			tTJSVariant result(array, array);
			array->Release();
			return result;
		}
		// Dictionary
		if (o1.IsInstanceOf(0, NULL, NULL, TJS_W("Dictionary"), NULL) == TJS_S_TRUE) {
			iTJSDispatch2 *dict = TJSCreateDictionaryObject();
			DictMemberCloneCaller *caller = new DictMemberCloneCaller(dict);
			tTJSVariantClosure closure(caller);
			o1.EnumMembers(TJS_IGNOREPROP, &closure, NULL);
			caller->Release();
			tTJSVariant result(dict, dict);
			dict->Release();
			return result;
		}
		// clone メソッド
		tTJSVariant result;
		static tjs_uint cloneHint = 0;
		if (o1.FuncCall(0, TJS_W("clone"), &cloneHint, &result, 0, NULL, NULL) == TJS_S_TRUE) {
			return result;
		}
	}
	return obj;
}

static bool ScriptsExEqualStruct(tTJSVariant v1, tTJSVariant v2)
{
	if (v1.Type() == tvtObject && v2.Type() == tvtObject) {
		if (v1.AsObjectNoAddRef() == v2.AsObjectNoAddRef()) return true;

		tTJSVariantClosure &o1 = v1.AsObjectClosureNoAddRef();
		tTJSVariantClosure &o2 = v2.AsObjectClosureNoAddRef();

		if (o1.IsInstanceOf(0, NULL, NULL, TJS_W("Function"), NULL) == TJS_S_TRUE
			&& o2.IsInstanceOf(0, NULL, NULL, TJS_W("Function"), NULL) == TJS_S_TRUE)
			return v1.DiscernCompare(v2);

		if (o1.IsInstanceOf(0, NULL, NULL, TJS_W("Array"), NULL) == TJS_S_TRUE
			&& o2.IsInstanceOf(0, NULL, NULL, TJS_W("Array"), NULL) == TJS_S_TRUE) {
			tTJSVariant o1Count, o2Count;
			(void)o1.PropGet(0, TJS_W("count"), &countHint, &o1Count, NULL);
			(void)o2.PropGet(0, TJS_W("count"), &countHint, &o2Count, NULL);
			if (!o1Count.DiscernCompare(o2Count)) return false;
			tjs_int count = o1Count;
			tTJSVariant o1Val, o2Val;
			for (tjs_int i = 0; i < count; i++) {
				(void)o1.PropGetByNum(TJS_IGNOREPROP, i, &o1Val, NULL);
				(void)o2.PropGetByNum(TJS_IGNOREPROP, i, &o2Val, NULL);
				if (!ScriptsExEqualStruct(o1Val, o2Val)) return false;
			}
			return true;
		}

		if (o1.IsInstanceOf(0, NULL, NULL, TJS_W("Dictionary"), NULL) == TJS_S_TRUE
			&& o2.IsInstanceOf(0, NULL, NULL, TJS_W("Dictionary"), NULL) == TJS_S_TRUE) {
			tTJSVariant k1, k2;
			_getKeys(&k1, v1);
			_getKeys(&k2, v2);
			if (!ScriptsExEqualStruct(k1, k2)) return false;
			DictMemberCompareCaller *caller = new DictMemberCompareCaller(o2);
			tTJSVariantClosure closure(caller);
			tTJSVariant(o1.EnumMembers(TJS_IGNOREPROP, &closure, NULL));
			bool result = caller->match;
			caller->Release();
			return result;
		}
	}
	return v1.DiscernCompare(v2);
}

//---------------------------------------------------------------------------
// Scripts クラスへのアタッチ
//---------------------------------------------------------------------------

class ScriptsAdd
{
public:
	// メンバ名一覧の取得
	static tjs_error TJS_INTF_METHOD getKeys(tTJSVariant *result,
		tjs_int numparams, tTJSVariant **param, iTJSDispatch2 *objthis) {
		if (numparams < 1) return TJS_E_BADPARAMCOUNT;
		_getKeys(result, *param[0]);
		return TJS_S_OK;
	}

	// メンバの個数の取得
	static tjs_error TJS_INTF_METHOD getCount(tTJSVariant *result,
		tjs_int numparams, tTJSVariant **param, iTJSDispatch2 *objthis) {
		if (numparams < 1) return TJS_E_BADPARAMCOUNT;
		if (result) {
			tjs_int count;
			param[0]->AsObjectClosureNoAddRef().GetCount(&count, NULL, NULL, NULL);
			*result = count;
		}
		return TJS_S_OK;
	}

	// コンテキストの取得
	static tjs_error TJS_INTF_METHOD getObjectContext(tTJSVariant *result,
		tjs_int numparams, tTJSVariant **param, iTJSDispatch2 *objthis) {
		if (numparams < 1) return TJS_E_BADPARAMCOUNT;
		if (result) {
			iTJSDispatch2 *o = param[0]->AsObjectClosureNoAddRef().ObjThis;
			*result = tTJSVariant(o, o);
		}
		return TJS_S_OK;
	}

	// コンテキストが null かどうか
	static tjs_error TJS_INTF_METHOD isNullContext(tTJSVariant *result,
		tjs_int numparams, tTJSVariant **param, iTJSDispatch2 *objthis) {
		if (numparams < 1) return TJS_E_BADPARAMCOUNT;
		if (result) *result = param[0]->AsObjectClosureNoAddRef().ObjThis == NULL;
		return TJS_S_OK;
	}

	// 構造体比較
	static tjs_error TJS_INTF_METHOD equalStruct(tTJSVariant *result,
		tjs_int numparams, tTJSVariant **param, iTJSDispatch2 *objthis) {
		if (numparams < 2) return TJS_E_BADPARAMCOUNT;
		if (result) *result = ScriptsExEqualStruct(*param[0], *param[1]);
		return TJS_S_OK;
	}

	// 全配列・辞書巡回
	static tjs_error TJS_INTF_METHOD foreach(tTJSVariant *result,
		tjs_int numparams, tTJSVariant **param, iTJSDispatch2 *objthis) {
		if (numparams < 2) return TJS_E_BADPARAMCOUNT;
		tTJSVariantClosure &obj = param[0]->AsObjectClosureNoAddRef();
		tTJSVariantClosure &funcClosure = param[1]->AsObjectClosureNoAddRef();

		iTJSDispatch2 *func     = funcClosure.Object;
		iTJSDispatch2 *functhis = funcClosure.ObjThis;
		if (functhis == 0) functhis = objthis;

		if (obj.IsInstanceOf(0, NULL, NULL, TJS_W("Array"), NULL) == TJS_S_TRUE) {
			tTJSVariant key, value;
			tTJSVariant **paramList = new tTJSVariant *[numparams];
			paramList[0] = &key;
			paramList[1] = &value;
			for (tjs_int i = 2; i < numparams; i++) paramList[i] = param[i];

			tTJSVariant arrayCount;
			(void)obj.PropGet(0, TJS_W("count"), &countHint, &arrayCount, NULL);
			tjs_int count = arrayCount;

			tTJSVariant breakResult;
			for (tjs_int i = 0; i < count; i++) {
				key = i;
				breakResult.Clear();
				(void)obj.PropGetByNum(TJS_IGNOREPROP, i, &value, NULL);
				(void)func->FuncCall(0, NULL, NULL, &breakResult, numparams, paramList, functhis);
				if (breakResult.Type() != tvtVoid) break;
			}
			if (result) *result = breakResult;
			delete[] paramList;
		} else {
			tTJSVariant **paramList = new tTJSVariant *[numparams];
			for (tjs_int i = 2; i < numparams; i++) paramList[i] = param[i];

			DictIterateCaller *caller = new DictIterateCaller(func, functhis, paramList, numparams);
			tTJSVariantClosure closure(caller);
			obj.EnumMembers(TJS_IGNOREPROP, &closure, NULL);
			if (result) *result = caller->breakResult;
			caller->Release();
			delete[] paramList;
		}
		return TJS_S_OK;
	}

	// octet の MD5 ハッシュ取得
	static tjs_error TJS_INTF_METHOD getMD5HashString(tTJSVariant *result,
		tjs_int numparams, tTJSVariant **param, iTJSDispatch2 *objthis) {
		if (numparams < 1) return TJS_E_BADPARAMCOUNT;
		tTJSVariantOctet *octet = param[0]->AsOctetNoAddRef();
		TVP_md5_state_t st;
		TVP_md5_init(&st);
		TVP_md5_append(&st, octet->GetData(), (int)octet->GetLength());
		tjs_uint8 buffer[16];
		TVP_md5_finish(&st, buffer);
		tjs_char ret[32 + 1];
		const tjs_char *hex = TJS_W("0123456789abcdef");
		for (tjs_int i = 0; i < 16; i++) {
			ret[i * 2]     = hex[(buffer[i] >> 4) & 0xF];
			ret[i * 2 + 1] = hex[buffer[i] & 0xF];
		}
		ret[32] = 0;
		if (result) *result = ttstr(ret);
		return TJS_S_OK;
	}

	// オブジェクト複製
	static tjs_error TJS_INTF_METHOD clone(tTJSVariant *result,
		tjs_int numparams, tTJSVariant **param, iTJSDispatch2 *objthis) {
		if (numparams < 1) return TJS_E_BADPARAMCOUNT;
		if (result) *result = ScriptsExClone(*param[0]);
		return TJS_S_OK;
	}
};

NCB_ATTACH_CLASS(ScriptsAdd, Scripts)
{
	RawCallback(TJS_W("getObjectKeys"),     &ScriptsAdd::getKeys,           TJS_STATICMEMBER);
	RawCallback(TJS_W("getObjectCount"),    &ScriptsAdd::getCount,          TJS_STATICMEMBER);
	RawCallback(TJS_W("getObjectContext"),  &ScriptsAdd::getObjectContext,  TJS_STATICMEMBER);
	RawCallback(TJS_W("isNullContext"),     &ScriptsAdd::isNullContext,     TJS_STATICMEMBER);
	RawCallback(TJS_W("equalStruct"),       &ScriptsAdd::equalStruct,       TJS_STATICMEMBER);
	RawCallback(TJS_W("foreach"),           &ScriptsAdd::foreach,           TJS_STATICMEMBER);
	RawCallback(TJS_W("getMD5HashString"),  &ScriptsAdd::getMD5HashString,  TJS_STATICMEMBER);
	RawCallback(TJS_W("clone"),             &ScriptsAdd::clone,             TJS_STATICMEMBER);
}
