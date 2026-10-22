import com.winlator.xconnector.XInputStream;
import com.winlator.xconnector.XOutputStream;
import com.winlator.xserver.XClient;
import com.winlator.xserver.XServer;
import com.winlator.xserver.errors.BadFence;
import com.winlator.xserver.extensions.SyncExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public class SyncFenceTest {
    static Method create;
    static Method await;
    static Method trigger;
    static Method destroy;
    static XClient client = new XClient();
    static XOutputStream output = new XOutputStream();

    static void invoke(Method method, SyncExtension sync, int id) throws Exception {
        method.invoke(sync, client, new XInputStream(id), output);
    }

    static Thread waiter(SyncExtension sync, int id, AtomicReference<Throwable> error) {
        Thread thread = new Thread(() -> {
            try { invoke(await, sync, id); }
            catch (InvocationTargetException e) { error.set(e.getCause()); }
            catch (Throwable e) { error.set(e); }
        });
        thread.start();
        return thread;
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Class<SyncExtension> type = SyncExtension.class;
        create = type.getDeclaredMethod("createFence", XClient.class, XInputStream.class, XOutputStream.class);
        await = type.getDeclaredMethod("awaitFence", XClient.class, XInputStream.class, XOutputStream.class);
        trigger = type.getDeclaredMethod("triggerFence", XClient.class, XInputStream.class, XOutputStream.class);
        destroy = type.getDeclaredMethod("destroyFence", XClient.class, XInputStream.class, XOutputStream.class);
        for (Method method : new Method[]{create, await, trigger, destroy}) method.setAccessible(true);
        SyncExtension sync = new SyncExtension(new XServer(), (byte)0);

        invoke(create, sync, 7);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = waiter(sync, 7, error);
        Thread.sleep(25);
        check(thread.isAlive(), "fence must wait");
        sync.setTriggered(7);
        thread.join(1000);
        check(!thread.isAlive() && error.get() == null, "external trigger must wake waiter");

        invoke(create, sync, 8);
        error.set(null);
        thread = waiter(sync, 8, error);
        Thread.sleep(25);
        invoke(trigger, sync, 8);
        thread.join(1000);
        check(!thread.isAlive() && error.get() == null, "protocol trigger must wake waiter");

        invoke(create, sync, 9);
        error.set(null);
        thread = waiter(sync, 9, error);
        Thread.sleep(25);
        invoke(destroy, sync, 9);
        thread.join(1000);
        check(!thread.isAlive() && error.get() instanceof BadFence, "destroyed fence must wake with error");

        invoke(create, sync, 10);
        error.set(null);
        thread = waiter(sync, 10, error);
        Thread.sleep(25);
        thread.interrupt();
        thread.join(1000);
        check(!thread.isAlive() && error.get() == null, "interruption must end wait");
        System.out.println("PASS: SYNC fence external trigger, protocol trigger, destroy and interrupt");
    }
}
