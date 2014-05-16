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

package edu.stanford.ramcloud;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This class provides Java bindings for RAMCloud. Right now it is a rather
 * simple subset of what RamCloud.h defines.
 *
 * Running ``javah'' on this file will generate a C header file with the
 * appropriate JNI function definitions. The glue interfacing to the C++
 * RAMCloud library can be found in JRamCloud.cc.
 *
 * For JNI information, the IBM tutorials and Android developer docs are much
 * better than Sun's at giving an overall intro:
 *      http://www.ibm.com/developerworks/java/tutorials/j-jni/section4.html
 *      http://developer.android.com/training/articles/perf-jni.html
 */
public class JRamCloud {
    private static final Logger log = LoggerFactory.getLogger(JRamCloud.class);

    static {
        try {
            System.loadLibrary("edu_stanford_ramcloud_JRamCloud");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Unable to load JNI binding. Please build RAMCloud Java bindings.");
            log.error("Unable to load JNI binding. Please build RAMCloud Java bindings.", e);
            System.exit(-1);
        }
    }

    public static final long VERSION_NONEXISTENT = 0L;

    /// Pointer to the underlying C++ RAMCloud object associated with this
    /// object.
    private long ramcloudObjectPointer = 0;

    /**
     * See src/RejectRules.h.
     */
    public static class RejectRules {
        // these needs to be in sync with JNI code;
        public static final int DoesntExist = 1;
        public static final int Exists = 1 << 1;
        public static final int VersionLeGiven = 1 << 2;
        public static final int VersionNeGiven = 1 << 3;

        protected long givenVersion;
        protected int flags;

        public RejectRules() {
            clear();
        }

        public void clear() {
            this.givenVersion = VERSION_NONEXISTENT;
            this.flags = 0x0;
        }

        public RejectRules set(int flags, long version) {
            this.flags = flags;
            this.givenVersion = version;
            return this;
        }

        public RejectRules set(int flags) {
            return set(flags,VERSION_NONEXISTENT);
        }

        public RejectRules rejectIfLeVersion(long version) {
            setVersion(version);
            this.flags |= VersionLeGiven;
            return this;
        }

        public RejectRules rejectIfExists() {
            this.flags |= Exists;
            return this;
        }

        public RejectRules rejectIfDoesntExists() {
            this.flags |= DoesntExist;
            return this;
        }

        public RejectRules rejectIfNeVersion(long version) {
            setVersion(version);
            this.flags |= VersionNeGiven;
            return this;
        }

        private void setVersion(long version) {
            this.givenVersion = version;
        }
    }

    public static class MultiReadObject {
        public long[] tableId;
        public byte[] key[];
        public short[] keyLength;

        public MultiReadObject(int size){
            this.tableId = new long[size];
            this.key = new byte[size][];
            this.keyLength = new short[size];
        }

        public void setObject(int num, long tableId, byte key[]){
            this.tableId[num] = tableId;
            this.key[num] = key;
            this.keyLength[num] = (short) this.key[num].length;
        }
    }

    public static class MultiWriteObject {
        public long[] tableId;
        public byte[] key[];
        public short[] keyLength;
        public byte[] value[];
        public int[] valueLength;
        public RejectRules[] rules;

        public MultiWriteObject(int size) {
            this.tableId = new long[size];
            this.key = new byte[size][];
            this.keyLength = new short[size];
            this.value = new byte[size][];
            this.valueLength = new int[size];
            this.rules = new RejectRules[size];
        }

        public void setObject(int num, long tableId, byte key[], byte value[], RejectRules rules){
            this.tableId[num] = tableId;
            this.key[num] = key;
            this.keyLength[num] = (short) key.length;
            this.value[num] = value;
            this.valueLength[num] = value.length;
            this.rules[num] = rules;
        }

    }

    public static class MultiWriteRspObject {
        private int status;
        private long version;

        public MultiWriteRspObject(int status, long version) {
            this.status = status;
            this.version = version;
        }
        public int getStatus() {
            return status;
        }

        public long getVersion() {
            return version;
        }
    }

    /**
     * This class is returned by Read operations. It encapsulates the entire
     * object, including the key, value, and version.
     *
     * It mostly exists because Java doesn't support primitive out parameters
     * or multiple return values, and we don't know the object's size ahead of
     * time, so passing in a fixed-length array would be problematic.
     */
    public static class Object {
        Object(byte[] _key, byte[] _value, long _version)
        {
            key = _key;
            value = _value;
            version = _version;
        }

        public String
        getKey()
        {
            return new String(key,StandardCharsets.UTF_8);
        }

        public String
        getValue()
        {
            return new String(value,StandardCharsets.UTF_8);
        }

        final public byte[] key;
        final public byte[] value;
        final public long version;
    }

    public static class TableEnumeratorObject {
        TableEnumeratorObject(Object[] _object, long _nextHash)
        {
            object = _object;
            nextHash = _nextHash;
        }

        final public Object[] object;
        final public long nextHash;
    }

    @Deprecated
    public class TableEnumerator {
        private long tableEnumeratorObjectPointer = 0;
        private long ramCloudObjectPointer = 0;

        public TableEnumerator(long tableId)
        {
            ramCloudObjectPointer = ramcloudObjectPointer;
            tableEnumeratorObjectPointer = init(tableId);
        }

        private native long init(long tableId);
        public native boolean hasNext();
        public native Object next();
    }

    public class TableEnumerator2 {

        protected long tableId;
        protected LinkedList<JRamCloud.Object> rcobjs = null;
        protected long nextHash = 0;
        protected boolean done = false;

        public TableEnumerator2(long tableId)
        {
            this.tableId = tableId;
            rcobjs = new LinkedList<>();
        }
        public boolean hasNext() {
            if (rcobjs.isEmpty())
            {
                if (done) {
                    return false;
                }
                JRamCloud.TableEnumeratorObject o = getTableObjects(this.tableId, this.nextHash);
                if (o.nextHash == 0L) {
                    done = true;
                }
                this.nextHash = o.nextHash;
                rcobjs.addAll(Arrays.asList(o.object));
                if (rcobjs.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        public Object next()
        {
            return rcobjs.pop();
        }
    }

    /**
     * Connect to the RAMCloud cluster specified by the given coordinator's
     * service locator string. This causes the JNI code to instantiate the
     * underlying RamCloud C++ object.
     */
    public
    JRamCloud(String coordinatorLocator, String clusterName)
    {
        ramcloudObjectPointer = connect(coordinatorLocator, clusterName);
    }

    public
    JRamCloud(String coordinatorLocator)
    {
        this(coordinatorLocator, "main");
    }

    /**
     * Disconnect from the RAMCloud cluster. This causes the JNI code to
     * destroy the underlying RamCloud C++ object.
     */
    public void
    disconnect()
    {
        if (ramcloudObjectPointer != 0) {
            disconnect(ramcloudObjectPointer);
            ramcloudObjectPointer = 0;
        }
    }

    /**
     * This method is called by the garbage collector before destroying the
     * object. The user really should have called disconnect, but in case
     * they did not, be sure to clean up after them.
     */
    @Override
    protected void
    finalize()
    {
        System.err.println("warning: JRamCloud::disconnect() was not called " +
                           "prior to the finalizer. You should disconnect " +
                           "your JRamCloud object when you're done with it.");
        disconnect();
    }

    /**
     * Convenience read() wrapper that take a String key argument.
     */
    public Object
    read(long tableId, String key)
    {
        return read(tableId, key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Convenience read() wrapper that take a String key argument.
     * @throws RejectRulesException
     */
    public Object
    read(long tableId, String key, RejectRules rules) throws RejectRulesException
    {
        return read(tableId, key.getBytes(StandardCharsets.UTF_8), rules);
    }

    /**
     * Convenience remove() wrapper that take a String key argument.
     */
    public long
    remove(long tableId, String key)
    {
        return remove(tableId, key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Convenience remove() wrapper that take a String key argument.
     * @throws RejectRulesException
     */
    public long
    remove(long tableId, String key, RejectRules rules) throws RejectRulesException
    {
        return remove(tableId, key.getBytes(StandardCharsets.UTF_8), rules);
    }

    /**
     * Convenience write() wrapper that take String key and value arguments.
     */
    public long
    write(long tableId, String key, String value)
    {
        return write(tableId, key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Convenience write() wrapper that take String key and value arguments.
     * @throws RejectRulesException
     */
    public long
    write(long tableId, String key, String value, RejectRules rules) throws RejectRulesException
    {
        return write(tableId, key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8), rules);
    }

    /**
     * Convenience write() wrapper that takes a String key and a byte[] value
     * argument.
     */
    public long
    write(long tableId, String key, byte[] value)
    {
        return write(tableId, key.getBytes(StandardCharsets.UTF_8), value);
    }

    /**
     * Convenience write() wrapper that takes a String key and a byte[] value
     * argument.
     * @throws RejectRulesException
     */
    public long
    write(long tableId, String key, byte[] value, RejectRules rules) throws RejectRulesException
    {
        return write(tableId, key.getBytes(StandardCharsets.UTF_8), value, rules);
    }

    private static native long connect(String coordinatorLocator, String clusterName);
    private static native void disconnect(long ramcloudObjectPointer);

    public native long createTable(String name);
    public native long createTable(String name, int serverSpan);
    public native void dropTable(String name);
    public native long getTableId(String name);
    public native Object read(long tableId, byte[] key);
    public native Object read(long tableId, byte[] key, RejectRules rules) throws RejectRulesException;
    public native Object[] multiRead(long[] tableId, byte[] keydata[], short[] keyDataSize, int requestNum);
    public native long remove(long tableId, byte[] key);
    public native long remove(long tableId, byte[] key, RejectRules rules) throws RejectRulesException;
    public native long write(long tableId, byte[] key, byte[] value);
    public native long write(long tableId, byte[] key, byte[] value, RejectRules rules) throws RejectRulesException;
    public native MultiWriteRspObject[] multiWrite(long[] tableId, byte[] key[], short[] keyDataSize, byte[] value[], int[] valueDataSize, int requestNum, RejectRules[] rules);
    public native TableEnumeratorObject getTableObjects(long tableId, long nextHash);
    public native long increment(long tableId, byte[] key, long incrementValue) throws ObjectDoesntExistException;

    /*
     * The following exceptions may be thrown by the JNI functions:
     */

    public static class TableDoesntExistException extends RuntimeException {
        public TableDoesntExistException(String message)
        {
            super(message);
        }
    }

    public static class ObjectDoesntExistException extends RejectRulesException {
        public ObjectDoesntExistException(String message)
        {
            super(message);
        }
    }

    public static class ObjectExistsException extends RejectRulesException {
        public ObjectExistsException(String message)
        {
            super(message);
        }
    }

    public static class WrongVersionException extends RejectRulesException {
        public WrongVersionException(String message)
        {
            super(message);
        }
    }

    public static class InvalidObjectException extends RuntimeException {
        public InvalidObjectException(String message) {
            super(message);
        }
    }

    public static class RejectRulesException extends Exception {
        public RejectRulesException(String message) {
            super(message);
        }
    }

    // TODO Define and support some of ClientException sub-classes.
    public static class ClientException extends Exception {
        public ClientException(String message) {
            super(message);
        }
    }

    public static void tableEnumeratorTest(JRamCloud ramcloud) {
        long startTime = 0;
        for (int x = 0 ; x < 2 ; x ++){
        for(int N = 1000; N < 10000; N += 1000) {
            long EnumerateTesttable = ramcloud.createTable("EnumerateTest");
            for(int i = 0 ; i < N ; ++i) {
                String key = String.valueOf(i);
                ramcloud.write(EnumerateTesttable, key.getBytes(StandardCharsets.UTF_8), "Hello, World!".getBytes(StandardCharsets.UTF_8));
            }

            long tableIdList[] = new long[N];
            byte[] keydata[] = new byte[N][];
            short keydataSize[] = new short[N];
            startTime = System.nanoTime();
            for (int j = 0 ; j < N ; ++j) {
                tableIdList[j] = EnumerateTesttable;
                String key = String.valueOf(j);
                keydata[j] = key.getBytes(StandardCharsets.UTF_8);
                keydataSize[j] = (short) keydata[j].length;
            }
            JRamCloud.Object out[] = ramcloud.multiRead(tableIdList, keydata, keydataSize, N);
            for (int i = 0; i < N; ++i) {
                if(out[i].version == 0) {
                        System.out.println("Verify fail " + out[i].getKey() + " V:" + out[i].getValue());
                }
            }

            System.out.println("multiRead           : " + N + " Time : " + (System.nanoTime()-startTime));

            startTime = System.nanoTime();
            JRamCloud.TableEnumerator tableEnum = ramcloud.new TableEnumerator(EnumerateTesttable);
            while (tableEnum.hasNext()) {
                Object tableEntry = tableEnum.next();
                if (tableEntry != null) {
                    System.out.println("tableEnumerator object: key = [" + tableEntry.getKey() + "], value = [" + tableEntry.getValue() + "]");
                }
            }
            System.out.println("old TableEnumerator : " + N + " Time : " + (System.nanoTime()-startTime));

            startTime = System.nanoTime();
            JRamCloud.TableEnumerator2 tableEnum2 = ramcloud.new TableEnumerator2(EnumerateTesttable);
            while (tableEnum2.hasNext()) {
                Object tableEntry2 = tableEnum2.next();
                if (tableEntry2 != null) {
                    System.out.println("tableEnumerator2 object: key = [" + tableEntry2.getKey() + "], value = [" + tableEntry2.getValue() + "]");
                }
            }
            System.out.println("new TableEnumerator : " + N + " Time : " + (System.nanoTime()-startTime));
            ramcloud.dropTable("EnumerateTest");
        }
        }
    }
    /**
     * A simple end-to-end test of the java bindings.
     */
    public static void
    main(String argv[])
    {
        JRamCloud ramcloud = new JRamCloud(argv[0]);
        long tableId = ramcloud.createTable("hi");
        System.out.println("created table, id = " + tableId);
        long tableId2 = ramcloud.getTableId("hi");
        System.out.println("getTableId says tableId = " + tableId2);

        System.out.println("wrote obj version = " +
            ramcloud.write(tableId, "thisIsTheKey", "thisIsTheValue"));

        JRamCloud.Object o = ramcloud.read(tableId, "thisIsTheKey");
        System.out.println("read object: key = [" + o.getKey() + "], value = ["
            + o.getValue() + "], version = " + o.version);

        ramcloud.remove(tableId, "thisIsTheKey");

        try {
            ramcloud.read(tableId, "thisIsTheKey");
            System.out.println("Error: shouldn't have read successfully!");
        } catch (Exception e) {
            // OK
        }

        ramcloud.write(tableId, "thisIsTheKey", "thisIsTheValue");

        long before = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            @SuppressWarnings("unused")
            JRamCloud.Object unused = ramcloud.read(tableId, "thisIsTheKey");
        }
        long after = System.nanoTime();
        System.out.println("Avg read latency: " +
            ((double)(after - before) / 1000 / 1000) + " usec");

        // multiRead test
        long tableId4 = ramcloud.createTable("table4");
        System.out.println("table4 id " + tableId4);
        ramcloud.write(tableId4, "object1-1", "value:1-1");
        ramcloud.write(tableId4, "object1-2", "value:1-2");
        ramcloud.write(tableId4, "object1-3", "value:1-3");
        long tableId5 = ramcloud.createTable("table5");
        System.out.println("table5 id " + tableId5);
        ramcloud.write(tableId5, "object2-1", "value:2-1");
        long tableId6 = ramcloud.createTable("table6");
        ramcloud.write(tableId6, "object3-1", "value:3-1");
        ramcloud.write(tableId6, "object3-2", "value:3-2");

        MultiReadObject mr = new MultiReadObject(2);
        MultiWriteObject mw = new MultiWriteObject(2);

        mr.setObject(0, tableId4, "object1-1".getBytes(StandardCharsets.UTF_8));
        mr.setObject(1, tableId5, "object2-1".getBytes(StandardCharsets.UTF_8));

        JRamCloud.Object out[] = ramcloud.multiRead(mr.tableId, mr.key, mr.keyLength, 2);
        for (int i = 0 ; i < 2 ; i++){
            System.out.println("multi read object: key = [" + out[i].getKey() + "], value = ["
                    + out[i].getValue() + "]");
        }

        for (int i = 0; i < 1000; i++) {
            String key1 = "key1" + String.valueOf(i);
            String key2 = "key2" + String.valueOf(i);

            mw.setObject(0, tableId4, key1.getBytes(StandardCharsets.UTF_8), "v0-value".getBytes(StandardCharsets.UTF_8), null);
            mw.setObject(1, tableId5, key2.getBytes(StandardCharsets.UTF_8), "v1".getBytes(StandardCharsets.UTF_8), null);

            MultiWriteRspObject[] rsp = ramcloud.multiWrite(mw.tableId, mw.key, mw.keyLength, mw.value, mw.valueLength, 2, mw.rules);
            if (rsp != null) {
                for (int j = 0; j < rsp.length; j++) {
                    System.out.println("multi write rsp(" + j + ") status:version " + rsp[j].getStatus() + ":" + rsp[j].getVersion());
                }
            }
        }
        for (int i = 0; i < 1000; i++) {
            String key1 = "key1" + String.valueOf(i);
            String key2 = "key2" + String.valueOf(i);

            mr.setObject(0, tableId4, key1.getBytes(StandardCharsets.UTF_8));
            mr.setObject(1, tableId5, key2.getBytes(StandardCharsets.UTF_8));

            out = ramcloud.multiRead(mr.tableId, mr.key, mr.keyLength, 2);
            for (int j = 0; j < 2; j++) {
                System.out.println("multi read object: key = [" + out[j].getKey() + "], value = [" + out[j].getValue() + "]");
            }
        }

        tableEnumeratorTest(ramcloud);

        ramcloud.dropTable("table4");
        ramcloud.dropTable("table5");
        ramcloud.dropTable("table6");
        ramcloud.disconnect();
    }
}
