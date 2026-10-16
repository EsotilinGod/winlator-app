import com.winlator.core.FrameTiming;

public class FrameTimingTest {
    static int checks;
    static void check(boolean ok) {checks++;if(!ok)throw new AssertionError("check "+checks);}
    static void near(float a,float b) {check(Math.abs(a-b)<0.02f);}
    public static void main(String[] args) throws Exception {
        FrameTiming t=new FrameTiming();
        check(t.snapshot(0).milliseconds.length==0);
        long now=0;t.record(now);
        check(t.snapshot(now).fps==0);
        for(int i=0;i<120;i++){now+=16666667;t.record(now);}
        FrameTiming.Snapshot s=t.snapshot(now);
        near(s.fps,60);near(s.meanMs,16.6667f);near(s.p99Ms,16.6667f);
        check(s.milliseconds.length==120);
        now+=100000000;t.record(now);now+=80000000;t.record(now);
        s=t.snapshot(now);near(s.peakMs,100);near(s.p99Ms,80);
        check(s.fps<60);near(s.milliseconds[119],80);
        s.milliseconds[119]=999;near(t.snapshot(now).milliseconds[119],80);
        for(int i=0;i<120;i++){now+=33333333;t.record(now);}
        s=t.snapshot(now);near(s.fps,30);near(s.peakMs,33.3333f);
        t.record(now);t.record(now-1);check(t.snapshot(now).milliseconds.length==120);
        check(t.snapshot(now+2000000001L).fps==0);
        now+=3000000000L;t.record(now);check(t.snapshot(now).milliseconds.length==0);
        now+=20000000;t.record(now);near(t.snapshot(now).fps,50);
        t.reset();check(t.snapshot(now).milliseconds.length==0);
        // Snapshot reads and writes may come from different threads.
        Thread producer=new Thread(()->{for(int i=0;i<10000;i++)t.record(10000000000L+i*1000000L);});
        producer.start();for(int i=0;i<1000;i++)check(t.snapshot(10000000000L).milliseconds.length<=120);
        producer.join();check(t.snapshot(19999000000L).milliseconds.length==120);
        System.out.println("PASS: "+checks+" HUD timing assertions");
    }
}
