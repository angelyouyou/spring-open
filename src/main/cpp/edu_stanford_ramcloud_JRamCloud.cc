/* Copyright (c) 2013 Stanford University
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR(S) DISCLAIM ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL AUTHORS BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

/*
 * Future TODO memo.
 * - Throwing exception is a bit expensive in Java. (Construct backtrace, etc.)
 *   Those native methods should be modified to notify conditional failure as
 *   part of return value object instead of exception to eliminate overhead.
 * - Inner classes in JRamCloud.java should be moved out to be a separate
 *   stand alone class, to eliminate workaround 00024 signature in
 *   C methods.
 * - Define and support some of ClientException sub-classes.
 *
 */

#include <RamCloud.h>
#include <TableEnumerator.h>
#include <Object.h>
#include "edu_stanford_ramcloud_JRamCloud.h"

using namespace RAMCloud;

/// Our JRamCloud java library is packaged under "edu.stanford.ramcloud".
/// We will need this when using FindClass, etc.
#define PACKAGE_PATH "edu/stanford/ramcloud/"

// Java RejectRules flags bit index
const static int DoesntExist = 1;
const static int Exists = 1 << 1;
const static int VersionLeGiven = 1 << 2;
const static int VersionNeGiven = 1 << 3;

#define check_null(var, msg)                                                \
    if (var == NULL) {                                                      \
        throw Exception(HERE, "JRamCloud: NULL returned: " msg "\n");       \
    }

/**
 * This class provides a simple means of extracting C-style strings
 * from a jstring and cleans up when the destructor is called. This
 * avoids having to manually do the annoying GetStringUTFChars /
 * ReleaseStringUTFChars dance.
 */
class JStringGetter {
  public:
    JStringGetter(JNIEnv* env, jstring jString)
        : env(env)
        , jString(jString)
        , string(env->GetStringUTFChars(jString, 0))
    {
        check_null(string, "GetStringUTFChars failed");
    }

    ~JStringGetter()
    {
        if (string != NULL)
            env->ReleaseStringUTFChars(jString, string);
    }

  private:
    JNIEnv* env;
    jstring jString;

  public:
    const char* const string;
};

/**
 * This class provides a simple means of accessing jbyteArrays as
 * C-style void* buffers and cleans up when the destructor is called.
 * This avoids having to manually do the annoying GetByteArrayElements /
 * ReleaseByteArrayElements dance.
 */
class JByteArrayGetter {
  public:
    JByteArrayGetter(JNIEnv* env, jbyteArray jByteArray)
        : env(env)
        , jByteArray(jByteArray)
        , pointer(static_cast<void*>(env->GetByteArrayElements(jByteArray, 0)))
        , length(env->GetArrayLength(jByteArray))
    {
        check_null(pointer, "GetByteArrayElements failed");
    }

    ~JByteArrayGetter()
    {
        if (pointer != NULL) {
            env->ReleaseByteArrayElements(jByteArray,
                                          reinterpret_cast<jbyte*>(pointer),
                                          0);
        }
    }

  private:
    JNIEnv* env;
    jbyteArray jByteArray;

  public:
    void* const pointer;
    const jsize length;
};

class JByteArrayReference {
  public:
    JByteArrayReference(JNIEnv* env, jbyteArray jByteArray)
        : env(env)
        , jByteArray(jByteArray)
        , pointer(static_cast<const void*>(env->GetByteArrayElements(jByteArray, 0)))
        , length(env->GetArrayLength(jByteArray))
    {
        check_null(pointer, "GetByteArrayElements failed");
    }

    ~JByteArrayReference()
    {
        if (pointer != NULL) {
            env->ReleaseByteArrayElements(jByteArray,
                                          (jbyte*)pointer,
                                          JNI_ABORT);
        }
    }

  private:
    JNIEnv* env;
    jbyteArray jByteArray;

  public:
    const void* const pointer;
    const jsize length;
};

static RamCloud*
getRamCloud(JNIEnv* env, jobject jRamCloud)
{
    const static jclass cls = (jclass)env->NewGlobalRef(env->FindClass(PACKAGE_PATH "JRamCloud"));
    const static jfieldID fieldId = env->GetFieldID(cls, "ramcloudObjectPointer", "J");
    return reinterpret_cast<RamCloud*>(env->GetLongField(jRamCloud, fieldId));
}

static TableEnumerator*
getTableEnumerator(JNIEnv* env, jobject jTableEnumerator)
{
    const static jclass cls = (jclass)env->NewGlobalRef(env->FindClass(PACKAGE_PATH "JRamCloud$TableEnumerator"));
    const static jfieldID fieldId = env->GetFieldID(cls, "tableEnumeratorObjectPointer", "J");
    return reinterpret_cast<TableEnumerator*>(env->GetLongField(jTableEnumerator, fieldId));
}

static void
createException(JNIEnv* env, jobject jRamCloud, const char* name)
{
    // Need to specify the full class name, including the package. To make it
    // slightly more complicated, our exceptions are nested under the JRamCloud
    // class.
    string fullName(PACKAGE_PATH "JRamCloud$");
    fullName += name;

    jclass cls = env->FindClass(fullName.c_str());
    check_null(cls, "FindClass failed");

    env->ThrowNew(cls, "");
}

static void
setRejectRules(JNIEnv* env, jobject jRejectRules, RejectRules& rules)
{
    const static jclass jc_RejectRules = (jclass) env->NewGlobalRef(
            env->FindClass(PACKAGE_PATH "JRamCloud$RejectRules"));
    static const jfieldID jf_flags = env->GetFieldID(jc_RejectRules, "flags", "I");
    check_null(jf_flags, "flags field ID is null");
    static const jfieldID jf_givenVersion = env->GetFieldID(jc_RejectRules,
            "givenVersion", "J");
    check_null(jf_givenVersion, "givenVersion field ID is null");

    const jint rejectFlag = env->GetIntField(jRejectRules, jf_flags);
    rules.doesntExist = (rejectFlag & DoesntExist) != 0;
    rules.exists = (rejectFlag & Exists) != 0;
    rules.versionLeGiven = (rejectFlag & VersionLeGiven) != 0;
    rules.versionNeGiven = (rejectFlag & VersionNeGiven) != 0;
    if (rules.versionLeGiven || rules.versionNeGiven) {
        rules.givenVersion = env->GetLongField(jRejectRules, jf_givenVersion);
    }
}

/**
 * This macro is used to catch C++ exceptions and convert them into Java
 * exceptions. Be sure to wrap the individual RamCloud:: calls in try blocks,
 * rather than the entire methods, since doing so with functions that return
 * non-void is a bad idea with undefined(?) behaviour.
 *
 * _returnValue is the value that should be returned from the JNI function
 * when an exception is caught and generated in Java. As far as I can tell,
 * the exception fires immediately upon returning from the JNI method. I
 * don't think anything else would make sense, but the JNI docs kind of
 * suck.
 */
#define EXCEPTION_CATCHER(_returnValue)                                        \
    catch (TableDoesntExistException& e) {                                     \
        createException(env, jRamCloud, "TableDoesntExistException");          \
        return _returnValue;                                                   \
    } catch (ObjectDoesntExistException& e) {                                  \
        createException(env, jRamCloud, "ObjectDoesntExistException");         \
        return _returnValue;                                                   \
    } catch (ObjectExistsException& e) {                                       \
        createException(env, jRamCloud, "ObjectExistsException");              \
        return _returnValue;                                                   \
    } catch (WrongVersionException& e) {                                       \
        createException(env, jRamCloud, "WrongVersionException");              \
        return _returnValue;                                                   \
    } catch (RejectRulesException& e) {                                        \
        createException(env, jRamCloud, "RejectRulesException");               \
        return _returnValue;                                                   \
    } catch (InvalidObjectException& e) {                                      \
        createException(env, jRamCloud, "InvalidObjectException");             \
        return _returnValue;                                                   \
    } catch (ClientException& e) {                                             \
        createException(env, jRamCloud, "ClientException");                    \
        return _returnValue;                                                   \
    }

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    connect
 * Signature: (Ljava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong
JNICALL Java_edu_stanford_ramcloud_JRamCloud_connect(JNIEnv *env,
                               jclass jRamCloud,
                               jstring coordinatorLocator,
                               jstring clusterName)
{
    JStringGetter locator(env, coordinatorLocator);
    JStringGetter cluster(env, clusterName);
    RamCloud* ramcloud = NULL;
    try {
        ramcloud = new RamCloud(locator.string, cluster.string);
    } EXCEPTION_CATCHER((jlong)(NULL));
    return reinterpret_cast<jlong>(ramcloud);
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    disconnect
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_edu_stanford_ramcloud_JRamCloud_disconnect(JNIEnv *env,
                                  jclass jRamCloud,
                                  jlong ramcloudObjectPointer)
{
    delete reinterpret_cast<RamCloud*>(ramcloudObjectPointer);
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    createTable
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jlong
JNICALL Java_edu_stanford_ramcloud_JRamCloud_createTable__Ljava_lang_String_2(JNIEnv *env,
                                                        jobject jRamCloud,
                                                        jstring jTableName)
{
    return Java_edu_stanford_ramcloud_JRamCloud_createTable__Ljava_lang_String_2I(env,
                                                            jRamCloud,
                                                            jTableName,
                                                            1);
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    createTable
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jlong
JNICALL Java_edu_stanford_ramcloud_JRamCloud_createTable__Ljava_lang_String_2I(JNIEnv *env,
                                                         jobject jRamCloud,
                                                         jstring jTableName,
                                                         jint jServerSpan)
{
    RamCloud* ramcloud = getRamCloud(env, jRamCloud);
    JStringGetter tableName(env, jTableName);
    uint64_t tableId;
    try {
        tableId = ramcloud->createTable(tableName.string, jServerSpan);
    } EXCEPTION_CATCHER(-1);
    return static_cast<jlong>(tableId);
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    dropTable
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT void
JNICALL Java_edu_stanford_ramcloud_JRamCloud_dropTable(JNIEnv *env,
                                 jobject jRamCloud,
                                 jstring jTableName)
{
    RamCloud* ramcloud = getRamCloud(env, jRamCloud);
    JStringGetter tableName(env, jTableName);
    try {
        ramcloud->dropTable(tableName.string);
    } EXCEPTION_CATCHER();
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    getTableId
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong
JNICALL Java_edu_stanford_ramcloud_JRamCloud_getTableId(JNIEnv *env,
                                  jobject jRamCloud,
                                  jstring jTableName)
{
    RamCloud* ramcloud = getRamCloud(env, jRamCloud);
    JStringGetter tableName(env, jTableName);
    uint64_t tableId;
    try {
        tableId = ramcloud->getTableId(tableName.string);
    } EXCEPTION_CATCHER(-1);
    return tableId;
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    read
 * Signature: (J[B)LJRamCloud/Object;
 */
JNIEXPORT jobject
JNICALL Java_edu_stanford_ramcloud_JRamCloud_read__J_3B(JNIEnv *env,
                                  jobject jRamCloud,
                                  jlong jTableId,
                                  jbyteArray jKey)
{
    RamCloud* ramcloud = getRamCloud(env, jRamCloud);
    JByteArrayReference key(env, jKey);

    Buffer buffer;
    uint64_t version;
    try {
        ramcloud->read(jTableId, key.pointer, key.length, &buffer, NULL, &version);
    } EXCEPTION_CATCHER(NULL);

    jbyteArray jValue = env->NewByteArray(buffer.getTotalLength());
    check_null(jValue, "NewByteArray failed");
    JByteArrayGetter value(env, jValue);
    buffer.copy(0, buffer.getTotalLength(), value.pointer);

    // Note that using 'javap -s' on the class file will print out the method
    // signatures (the third argument to GetMethodID).
    const static jclass cls = (jclass)env->NewGlobalRef(env->FindClass(PACKAGE_PATH "JRamCloud$Object"));
    check_null(cls, "FindClass failed");

    const static jmethodID methodId = env->GetMethodID(cls,
                                          "<init>",
                                          "([B[BJ)V");
    check_null(methodId, "GetMethodID failed");

    return env->NewObject(cls,
                          methodId,
                          jKey,
                          jValue,
                          static_cast<jlong>(version));
}

// Workaround for javah generating incorrect signature for inner class
// 00024 is an escaped signature for $ character
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jobject JNICALL Java_edu_stanford_ramcloud_JRamCloud_read__J_3BLedu_stanford_ramcloud_JRamCloud_00024RejectRules_2
  (JNIEnv *, jobject, jlong, jbyteArray, jobject);
#ifdef __cplusplus
}
#endif

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    read
 * Signature: (J[BLedu/stanford/ramcloud/JRamCloud/RejectRules;)Ledu/stanford/ramcloud/JRamCloud/Object;
 */
JNIEXPORT jobject
JNICALL Java_edu_stanford_ramcloud_JRamCloud_read__J_3BLedu_stanford_ramcloud_JRamCloud_00024RejectRules_2(JNIEnv *env,
                                                          jobject jRamCloud,
                                                          jlong jTableId,
                                                          jbyteArray jKey,
                                                          jobject jRejectRules)
{
    RamCloud* ramcloud = getRamCloud(env, jRamCloud);
    JByteArrayReference key(env, jKey);

    Buffer buffer;
    uint64_t version;
    RejectRules rules = {};
    setRejectRules(env, jRejectRules, rules);

    try {
        ramcloud->read(jTableId, key.pointer, key.length, &buffer, &rules, &version);
    } EXCEPTION_CATCHER(NULL);

    jbyteArray jValue = env->NewByteArray(buffer.getTotalLength());
    check_null(jValue, "NewByteArray failed");
    JByteArrayGetter value(env, jValue);
    buffer.copy(0, buffer.getTotalLength(), value.pointer);

    // Note that using 'javap -s' on the class file will print out the method
    // signatures (the third argument to GetMethodID).
    const static jclass cls = (jclass)env->NewGlobalRef(env->FindClass(PACKAGE_PATH "JRamCloud$Object"));
    check_null(cls, "FindClass failed");

    const static jmethodID methodId = env->GetMethodID(cls,
                                          "<init>",
                                          "([B[BJ)V");
    check_null(methodId, "GetMethodID failed");

    return env->NewObject(cls,
                          methodId,
                          jKey,
                          jValue,
                          static_cast<jlong>(version));
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    multiRead
 * Signature: ([Ledu/stanford/ramcloud/JRamCloud$multiReadObject;I)[Ledu/stanford/ramcloud/JRamCloud$Object;
 */
JNIEXPORT jobjectArray
JNICALL Java_edu_stanford_ramcloud_JRamCloud_multiRead(JNIEnv *env,
                                                        jobject jRamCloud,
							jlongArray jTableId,
							jobjectArray jKeyData,
							jshortArray jKeyLength,
							jint jrequestNum){

    RamCloud* ramcloud = getRamCloud(env, jRamCloud);
    MultiReadObject objects[jrequestNum];
    Tub<Buffer> values[jrequestNum];
    jbyteArray jKey[jrequestNum];
    MultiReadObject* requests[jrequestNum];

    jlong tableId;
    jshort keyLength;
    jbyte* keyData[jrequestNum];

    for (int i = 0 ; i < jrequestNum ; i++){
        jKey[i] = (jbyteArray)env->GetObjectArrayElement(jKeyData, i);

        env->GetShortArrayRegion(jKeyLength, i, 1, &keyLength);

        keyData[i] = (jbyte *) malloc(keyLength);
        env->GetByteArrayRegion(jKey[i], 0, keyLength, keyData[i]);

        env->GetLongArrayRegion(jTableId, i, 1, &tableId);
        objects[i].tableId = tableId;
        objects[i].key = keyData[i];
        objects[i].keyLength = keyLength;
        objects[i].value = &values[i];
        requests[i] = &objects[i];
    }

    try {
        ramcloud->multiRead(requests, jrequestNum);
    } EXCEPTION_CATCHER(NULL);

    const static jclass jc_RcObject = (jclass)env->NewGlobalRef(env->FindClass(PACKAGE_PATH "JRamCloud$Object"));
    check_null(jc_RcObject, "FindClass failed");
    const static jmethodID jm_init = env->GetMethodID(jc_RcObject,
                                        "<init>",
                                        "([B[BJ)V");

    jobjectArray outJNIArray = env->NewObjectArray(jrequestNum, jc_RcObject , NULL);
    check_null(outJNIArray, "NewObjectArray failed");

    for (int i = 0 ; i < jrequestNum ; i++) {
	if (objects[i].status == 0) {
	    jbyteArray jValue = env->NewByteArray(values[i].get()->getTotalLength());
	    check_null(jValue, "NewByteArray failed");
	    JByteArrayGetter value(env, jValue);
	    values[i].get()->copy(0, values[i].get()->getTotalLength(), value.pointer);
	    jobject obj = env->NewObject(jc_RcObject, jm_init, jKey[i], jValue, (jlong)objects[i].version);
	    check_null(obj, "NewObject failed");
	    env->SetObjectArrayElement(outJNIArray, i, obj);
	}
	free(keyData[i]);
    }
    return outJNIArray;
}


/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    remove
 * Signature: (J[B)J
 */
JNIEXPORT jlong
JNICALL Java_edu_stanford_ramcloud_JRamCloud_remove__J_3B(JNIEnv *env,
                                    jobject jRamCloud,
                                    jlong jTableId,
                                    jbyteArray jKey)
{
    RamCloud* ramcloud = getRamCloud(env, jRamCloud);
    JByteArrayReference key(env, jKey);
    uint64_t version = VERSION_NONEXISTENT;
    try {
        ramcloud->remove(jTableId, key.pointer, key.length, NULL, &version);
    } EXCEPTION_CATCHER(-1);
    return static_cast<jlong>(version);
}


// Workaround for javah generating incorrect signature for inner class
// 00024 is an escaped signature for $ character
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jlong JNICALL Java_edu_stanford_ramcloud_JRamCloud_remove__J_3BLedu_stanford_ramcloud_JRamCloud_00024RejectRules_2
  (JNIEnv *, jobject, jlong, jbyteArray, jobject);
#ifdef __cplusplus
}
#endif

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    remove
 * Signature: (J[BLedu/stanford/ramcloud/JRamCloud/$RejectRules;)J
 */
JNIEXPORT jlong
JNICALL Java_edu_stanford_ramcloud_JRamCloud_remove__J_3BLedu_stanford_ramcloud_JRamCloud_00024RejectRules_2(JNIEnv *env,
                                                           jobject jRamCloud,
                                                           jlong jTableId,
                                                           jbyteArray jKey,
                                                           jobject jRejectRules)
{
    RamCloud* ramcloud = getRamCloud(env, jRamCloud);
    JByteArrayReference key(env, jKey);
    RejectRules rules = {};
    setRejectRules(env, jRejectRules, rules);
    uint64_t version = VERSION_NONEXISTENT;
    try {
        ramcloud->remove(jTableId, key.pointer, key.length, &rules, &version);
    } EXCEPTION_CATCHER(-1);
    return static_cast<jlong>(version);
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    write
 * Signature: (J[B[B)J
 */
JNIEXPORT jlong
JNICALL Java_edu_stanford_ramcloud_JRamCloud_write__J_3B_3B(JNIEnv *env,
                                      jobject jRamCloud,
                                      jlong jTableId,
                                      jbyteArray jKey,
                                      jbyteArray jValue)
{
    RamCloud* ramcloud = getRamCloud(env, jRamCloud);
    JByteArrayReference key(env, jKey);
    JByteArrayGetter value(env, jValue);
    uint64_t version;
    try {
        ramcloud->write(jTableId,
                        key.pointer, key.length,
                        value.pointer, value.length,
                        NULL,
                        &version);
    } EXCEPTION_CATCHER(-1);
    return static_cast<jlong>(version);
}

// Workaround for javah generating incorrect signature for inner class
// 00024 is an escaped signature for $ character
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jlong JNICALL Java_edu_stanford_ramcloud_JRamCloud_write__J_3B_3BLedu_stanford_ramcloud_JRamCloud_00024RejectRules_2
  (JNIEnv *, jobject, jlong, jbyteArray, jbyteArray, jobject);
#ifdef __cplusplus
}
#endif

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    write
 * Signature: (J[B[BLedu/stanford/ramcloud/JRamCloud/RejectRules;)J
 */
JNIEXPORT jlong
JNICALL Java_edu_stanford_ramcloud_JRamCloud_write__J_3B_3BLedu_stanford_ramcloud_JRamCloud_00024RejectRules_2(JNIEnv *env,
                                                           jobject jRamCloud,
                                                           jlong jTableId,
                                                           jbyteArray jKey,
                                                           jbyteArray jValue,
                                                           jobject jRejectRules)
{
    RamCloud* ramcloud = getRamCloud(env, jRamCloud);
    JByteArrayReference key(env, jKey);
    JByteArrayGetter value(env, jValue);
    RejectRules rules = {};
    setRejectRules(env, jRejectRules, rules);
    uint64_t version;
    try {
        ramcloud->write(jTableId,
                key.pointer, key.length,
                value.pointer, value.length,
                &rules,
                &version);
    } EXCEPTION_CATCHER(-1);
    return static_cast<jlong> (version);
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud_TableEnumerator
 * Method:    init
 * Signature: (J)V
 */
JNIEXPORT jlong JNICALL Java_edu_stanford_ramcloud_JRamCloud_00024TableEnumerator_init(JNIEnv *env,
                                                                                      jobject jTableEnumerator,
                                                                                      jlong jTableId)
{
    const static jclass cls = (jclass)env->NewGlobalRef(env->FindClass(PACKAGE_PATH "JRamCloud$TableEnumerator"));
    const static jfieldID fieldId = env->GetFieldID(cls, "ramCloudObjectPointer", "J");
    RamCloud* ramcloud = reinterpret_cast<RamCloud*>(env->GetLongField(jTableEnumerator, fieldId));

    return reinterpret_cast<jlong>(new TableEnumerator(*ramcloud, jTableId, false));
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud_TableEnumerator
 * Method:    hasNext
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_edu_stanford_ramcloud_JRamCloud_00024TableEnumerator_hasNext( JNIEnv *env,
                                                                                              jobject jTableEnumerator)
{
    TableEnumerator* tableEnum = getTableEnumerator(env, jTableEnumerator);
    return static_cast<jboolean>(tableEnum->hasNext());
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud_TableEnumerator
 * Method:    next
 * Signature: ()Ledu/stanford/ramcloud/JRamCloud/Object;
 */
JNIEXPORT jobject JNICALL Java_edu_stanford_ramcloud_JRamCloud_00024TableEnumerator_next( JNIEnv *env,
                                                                                          jobject jTableEnumerator)
{
    TableEnumerator* tableEnum = getTableEnumerator(env, jTableEnumerator);

    if (tableEnum->hasNext()) {
        uint32_t size = 0;
        const void* buffer = 0;
        uint64_t version = 0;

        tableEnum->next(&size, &buffer);
        Object object(buffer, size);

        jbyteArray jKey = env->NewByteArray(object.getKeyLength());
        jbyteArray jValue = env->NewByteArray(object.getDataLength());

        JByteArrayGetter key(env, jKey);
        JByteArrayGetter value(env, jValue);

        memcpy(key.pointer, object.getKey(), object.getKeyLength());
        memcpy(value.pointer, object.getData(), object.getDataLength());

        version = object.getVersion();

        const static jclass cls = (jclass)env->NewGlobalRef(env->FindClass(PACKAGE_PATH "JRamCloud$Object"));
        check_null(cls, "FindClass failed");
        const static jmethodID methodId = env->GetMethodID(cls,
                                          "<init>",
                                          "([B[BJ)V");
        check_null(methodId, "GetMethodID failed");
        return env->NewObject(cls,
                              methodId,
                              jKey,
                              jValue,
                              static_cast<jlong>(version));
    } else {
        return NULL;
    }
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    getTableObjects
 * Signature: (JJ)Ledu/stanford/ramcloud/JRamCloud/TableEnumeratorObject;
 */
JNIEXPORT jobject JNICALL Java_edu_stanford_ramcloud_JRamCloud_getTableObjects(JNIEnv *env,
                                                                               jobject jRamCloud,
                                                                               jlong jTableId,
                                                                               jlong jTabletNextHash){

    RamCloud* ramcloud = getRamCloud(env, jRamCloud);

    const static jclass jc_RcObject = (jclass)env->NewGlobalRef(env->FindClass(PACKAGE_PATH "JRamCloud$Object"));
    check_null(jc_RcObject, "FindClass failed");
    const static jmethodID jm_init = env->GetMethodID(jc_RcObject,
                                        "<init>",
                                        "([B[BJ)V");
    check_null(jm_init, "GetMethodID failed");

    const static jclass jc_RcTableObject = (jclass)env->NewGlobalRef(env->FindClass(PACKAGE_PATH "JRamCloud$TableEnumeratorObject"));
    check_null(jc_RcTableObject, "FindClass failed");
    const static jmethodID jm_TableEnumeratorObject_init = env->GetMethodID(jc_RcTableObject,
                                        "<init>",
                                        "([L" PACKAGE_PATH "JRamCloud$Object;J)V");
    check_null(jm_TableEnumeratorObject_init, "GetMethodID failed");


    Buffer state;
    Buffer objects;
    bool done = false;

    while (true) {
        jTabletNextHash = ramcloud->enumerateTable(jTableId, false, jTabletNextHash, state, objects);
        if (objects.getTotalLength() > 0) {
            break;
        }
        if (objects.getTotalLength() == 0 && jTabletNextHash == 0) {
            done = true;
            break;
        }
    }

    if (done) {
        return env->NewObject(jc_RcTableObject, jm_TableEnumeratorObject_init, env->NewObjectArray(0, jc_RcObject , NULL), 0);
    }

    int numOfObjects = 0;
    uint32_t nextOffset = 0;
    for (numOfObjects = 0; nextOffset < objects.getTotalLength() ; numOfObjects++) {
        uint32_t objectSize = *objects.getOffset<uint32_t>(nextOffset);
        nextOffset += downCast<uint32_t>(sizeof(uint32_t));
        nextOffset += objectSize;
    }

    jobjectArray outJNIArray = env->NewObjectArray(numOfObjects, jc_RcObject , NULL);
    check_null(outJNIArray, "NewObjectArray failed");

    nextOffset = 0;
    for (int i = 0; nextOffset < objects.getTotalLength() ;i++) {
        uint32_t objectSize = *objects.getOffset<uint32_t>(nextOffset);
        nextOffset += downCast<uint32_t>(sizeof(uint32_t));

        const void* blob = objects.getRange(nextOffset, objectSize);
        nextOffset += objectSize;

        Object object(blob, objectSize);

        jbyteArray jKey = env->NewByteArray(object.getKeyLength());
        jbyteArray jValue = env->NewByteArray(object.getDataLength());

        JByteArrayGetter key(env, jKey);
        JByteArrayGetter value(env, jValue);

        memcpy(key.pointer, object.getKey(), object.getKeyLength());
        memcpy(value.pointer, object.getData(), object.getDataLength());

        uint64_t version = object.getVersion();

        jobject obj = env->NewObject(jc_RcObject, jm_init, jKey, jValue, static_cast<jlong>(version));
        check_null(obj, "NewObject failed");

        env->SetObjectArrayElement(outJNIArray, i, obj);
    }

    return env->NewObject(jc_RcTableObject, jm_TableEnumeratorObject_init, outJNIArray, jTabletNextHash);
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    multiWrite
 * Signature: ([Ledu/stanford/ramcloud/JRamCloud/MultiWriteObject;)[Ledu/stanford/ramcloud/JRamCloud/MultiWriteRspObject;
 */
JNIEXPORT jobjectArray JNICALL Java_edu_stanford_ramcloud_JRamCloud_multiWrite(JNIEnv *env,
	                                                                       jobject jRamCloud,
	                                                                       jlongArray jTableId,
	                                                                       jobjectArray jKeyData,
	                                                                       jshortArray jKeyLength,
	                                                                       jobjectArray jValueData,
	                                                                       jintArray jValueLength,
	                                                                       jint jrequestNum,
	                                                                       jobjectArray jRules ) {

    RamCloud* ramcloud = getRamCloud(env, jRamCloud);
    Tub<MultiWriteObject> objects[jrequestNum];
    MultiWriteObject *requests[jrequestNum];
    RejectRules rules[jrequestNum];
    jbyteArray jKey[jrequestNum];
    jbyteArray jValue[jrequestNum];

    jlong tableId;
    jshort keyLength;
    jint valueLength;
    jbyte* keyData[jrequestNum];
    jbyte* valueData[jrequestNum];

    const static jclass jc_RejectRules = (jclass)env->NewGlobalRef(env->FindClass(PACKAGE_PATH "JRamCloud$RejectRules"));

    const static jfieldID jf_givenVersion = env->GetFieldID(jc_RejectRules, "givenVersion", "J");
    check_null(jf_givenVersion, "givenVersion field ID is null");

    const static jfieldID jf_flags = env->GetFieldID(jc_RejectRules, "flags", "I");
    check_null(jf_flags, "flags field ID is null");

    for (int i = 0; i < jrequestNum; i++) {
        env->GetLongArrayRegion(jTableId, i, 1, &tableId);

        env->GetShortArrayRegion(jKeyLength, i, 1, &keyLength);
        jKey[i] = (jbyteArray)env->GetObjectArrayElement(jKeyData, i);
        keyData[i] = (jbyte *) malloc(keyLength);
        env->GetByteArrayRegion(jKey[i], 0, keyLength, keyData[i]);

        env->GetIntArrayRegion(jValueLength, i, 1, &valueLength);
        jValue[i] = (jbyteArray)env->GetObjectArrayElement(jValueData, i);
        valueData[i] = (jbyte *) malloc(valueLength);
        env->GetByteArrayRegion(jValue[i], 0, valueLength, valueData[i]);

        jobject jRejectRules = (jbyteArray)env->GetObjectArrayElement(jRules, i);
        rules[i] = {};

        if (jRejectRules != NULL) {
            const jint flags = env->GetIntField(jRejectRules, jf_flags);

            rules[i].doesntExist = (flags & DoesntExist) != 0;

            rules[i].exists = (flags & Exists) != 0;

            rules[i].versionLeGiven = (flags & VersionLeGiven) != 0;

            rules[i].versionNeGiven = (flags & VersionNeGiven) != 0;

            if (rules[i].versionLeGiven || rules[i].versionNeGiven) {
                rules[i].givenVersion = env->GetLongField(jRejectRules, jf_givenVersion);
            }
        }
        objects[i].construct(tableId, keyData[i], keyLength, valueData[i], valueLength, &rules[i]);
        requests[i] = objects[i].get();
    }
    try {
        ramcloud->multiWrite(requests, jrequestNum);
    } EXCEPTION_CATCHER(NULL);

    const static jclass jc_RcObject = (jclass)env->NewGlobalRef(env->FindClass(PACKAGE_PATH "JRamCloud$MultiWriteRspObject"));
    check_null(jc_RcObject, "FindClass failed");
    const static jmethodID jm_init = env->GetMethodID(jc_RcObject,
                                        "<init>",
                                        "(IJ)V");

    jobjectArray outJNIArray = env->NewObjectArray(jrequestNum, jc_RcObject , NULL);
    check_null(outJNIArray, "NewObjectArray failed");

    for (int i = 0 ; i < jrequestNum ; i++) {
        jobject obj = env->NewObject(jc_RcObject, jm_init, objects[i]->status, objects[i]->version);
        check_null(obj, "NewObject failed");
        env->SetObjectArrayElement(outJNIArray, i, obj);
        free(keyData[i]);
        free(valueData[i]);
        objects[i].destroy();
    }
    return outJNIArray;
}

/*
 * Class:     edu_stanford_ramcloud_JRamCloud
 * Method:    increment
 * Signature: (J[BJ)J
 */
JNIEXPORT jlong JNICALL Java_edu_stanford_ramcloud_JRamCloud_increment (JNIEnv* env, jobject jRamCloud, jlong jTableId, jbyteArray jKey, jlong incrementValue) {
    RamCloud* ramcloud = getRamCloud(env, jRamCloud);
    JByteArrayReference key(env, jKey);
    uint64_t version = VERSION_NONEXISTENT;
    try {
        return ramcloud->increment(jTableId, key.pointer, key.length, incrementValue, NULL, &version);
    } EXCEPTION_CATCHER(VERSION_NONEXISTENT);
}
