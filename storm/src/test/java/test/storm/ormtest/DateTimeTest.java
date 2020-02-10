package test.storm.ormtest;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slowcoders.util.Debug;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.ref.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.JVM)
public class DateTimeTest  {

	static Immortal __ghost;
	static ReferenceQueue refQ;
	static Reference refRoot;
	static Reference refImmortal;
	static Reference refMortal;

	private interface RefCreator {
		Reference create(Object ref);

	}

	class Root {
		Immortal immortal;
		Mortal mortal;

		Root() {
			immortal = new Immortal();
			mortal = new Mortal();
		}

		@Override
		protected void finalize() throws Throwable {
			__ghost = immortal;
			System.out.println("finalize root");
		}
	}

	class Immortal {
		@Override
		protected void finalize() throws Throwable {
			System.out.println("finalize Immortal");
		}
	}

	class Mortal {
		@Override
		protected void finalize() throws Throwable {
			System.out.println("finalize mortal");
		}
	}

	class PhantomA extends PhantomReference {
		public PhantomA(Object referent, ReferenceQueue q) {
			super(referent, q);
		}
	}

	enum RefType implements RefCreator {
		Weak { public Reference create(Object ref) { return new WeakReference<>(ref, refQ); } },
		Soft { public Reference create(Object ref) { return new SoftReference<>(ref, refQ); } },
		Phantom { public Reference create(Object ref) { return new PhantomReference<>(ref, refQ); } },

	}

	void makeTestRef(RefType refType) {
		refQ = new ReferenceQueue<>();
		Root root = new Root();
		refRoot = refType.create(root);
		refImmortal = refType.create(root.immortal);
		refMortal = refType.create(root.mortal);
	}

	private void checkGCResult(int count) {
		System.gc();
		System.out.println("gc " + count + " is done " + refQ.poll() + ", " + refQ.poll() + ", " + refQ.poll());
		System.runFinalization();
		System.out.println("------------------");
	}

	void testRef(RefType refType) {
		makeTestRef(refType);
		checkGCResult(1);
		checkGCResult(2);
		checkGCResult(3);
		checkGCResult(4);
		System.out.println("end of test " + refType + " reference.");
	}

	@Test public void testPhantom() {
		// gc-1: finalize 3 회
		// gc-2: finalize 실제로 memory 에서 제거되기 직전에 refQ 에 enqueue [Immoortal 을 제외한 2 개]
		testRef(RefType.Phantom);
	}

	@Test public void testWeak() {
		// gc-1: finalize 3 회
		// gc-2: finalize 후 refQ 에 [3개 모두] enqueue
		testRef(RefType.Weak);
	}

	@Test public void testSoft() {
		testRef(RefType.Soft);
	}

	void makeStrongRef() {
		{
			new Root();
		}
	}

	@Test public void testFinalize() {
		makeStrongRef();
		System.gc();
		System.runFinalization();
		System.out.println("------------------");
		System.gc();
		System.runFinalization();
		System.out.println("------------------");
	}

	private static ByteBuffer allocateMapped(int size) throws Exception {
		File f = File.createTempFile("mapped", "tmp");
		f.deleteOnExit();
		RandomAccessFile raf = new RandomAccessFile(f, "rw");
		raf.setLength(size);
		FileChannel ch = raf.getChannel();
		MappedByteBuffer result = ch.map(FileChannel.MapMode.READ_WRITE, 0, size);
		ch.close();
		return result;
	}

	private void assertPutByteBuffer(ByteBuffer dst, ByteBuffer src, boolean readOnly) {
		// Start 'dst' off as the index-identity pattern.
		for (int i = 0; i < dst.capacity(); ++i) {
			dst.put(i, (byte) i);
		}
		// Deliberately offset the position we'll write to by 1.
		dst.position(1);

		// Make the source more interesting.
		for (int i = 0; i < src.capacity(); ++i) {
			src.put(i, (byte) (16 + i));
		}
		if (readOnly) {
			src = src.asReadOnlyBuffer();
		}

		ByteBuffer dst2 = dst.put(src);
		assertSame(dst, dst2);
		assertEquals(0, src.remaining());
		assertEquals(src.position(), src.capacity());
		assertEquals(dst.position(), src.capacity() + 1);
		for (int i = 0; i < src.capacity(); ++i) {
			assertEquals(src.get(i), dst.get(i + 1));
		}

		// No room for another.
		src.position(0);
		try {
			dst.put(src);
			fail();
		} catch (BufferOverflowException expected) {
		}
	}

	@Test public void testMappedByteBuffer() throws Exception {
		assertPutByteBuffer(ByteBuffer.allocate(10), allocateMapped(8), false);
	}

	@Test public void testDateTime_01() {
		DateTime local = new DateTime(0);
		DateTime utc = local.toDateTime(DateTimeZone.UTC);
		DateTime ny = local.toDateTime(DateTimeZone.forID("Canada/Pacific"));
		
		System.out.println(local.toString());
		System.out.println(utc.toString() + " " + utc.getZone());
		System.out.println(ny.toString() + " " + utc.getZone());
		
		DateTime start = new DateTime(local.getZone().convertLocalToUTC(0, false), local.getZone());
		System.out.println(start.toString() + " ==== " + utc.getZone());
		
		//assertEquals(dt, local);
		//assertEquals(dt, utc);
		//assertEquals(dt, utc2);
	}
	
	@Test public void testLocalDate() {
		LocalDate dt = LocalDate.now();
		DateTime utc = dt.toDateTimeAtStartOfDay();
		
		System.out.println(dt.toString() + " " + "NoTimezone");
		System.out.println(utc.toString() + " " + utc.getZone());
		//assertEquals(dt, local);
		//assertEquals(dt, utc);
		//assertEquals(dt, utc2);
	}

	@Test public void testWeakHashMap() {
		WeakHashMap<Object, Object> map = new WeakHashMap<>();
		Integer list[] = new Integer[10_0000];
		long t0 = System.currentTimeMillis();
		for (int i = list.length; --i >= 0; ) {
			list[i] = new Integer(i);
			map.put(list[i], new Long(i));
		}
		long t1 = System.currentTimeMillis();
		System.out.println("create 100k map: " + (t1 - t0));

		for (int i = list.length; --i >= 0; ) {
			Long v = (Long)map.get(list[i]);
			if (v.intValue() != i) {
				Debug.wtf("????");
			}
		}

		long t2 = System.currentTimeMillis();
		System.out.println("search 100k map: " + (t2 - t1));
	}

	static class $Test22 {
		static int FF;
		static {
			System.out.println("+++++++++++++++++++++++++++++");
			FF = 2020;
		}
	};




	@Test public void testHashMap() throws Exception {

		;
		Class<?> c = $Test22.class;
		c.getDeclaredFields();
		Field ff = c.getDeclaredField("FF");
		int v = (int)ff.get(null);
		c = Class.forName("test.storm.ormtest.DateTimeTest$$Test22");
		Runnable r = new Runnable() {

			@Override
			public void run() {
				Class c = this.getClass();
				System.out.println("-- " + c.getSimpleName());
				System.out.println("-- " + c.getName());
				System.out.println("-- " + c.getCanonicalName());
				System.out.println("-- " + c.getPackage().getName());
				System.out.println("-- " + Modifier.isTransient(c.getModifiers()));
			}
		};
		r.run();
		System.out.println("-- " + c.getSimpleName());
		System.out.println("-- " + c.getName());
		System.out.println("-- " + c.getCanonicalName());
		c = Class.forName("test.storm.ormtest.DateTimeTest.$Test22");

		HashMap<String, Object> editMap = new HashMap<>();
		editMap.put("1", "1");
		editMap.put("2", "1");
		editMap.put("3", "1");
		editMap.put("4", "1");

		System.out.println(editMap);
		for (Iterator<Map.Entry<String, Object>> it = editMap.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, Object> entry = it.next();
			it.remove();
		}
		System.out.println("===========================");
		System.out.println(editMap);
	}

}
