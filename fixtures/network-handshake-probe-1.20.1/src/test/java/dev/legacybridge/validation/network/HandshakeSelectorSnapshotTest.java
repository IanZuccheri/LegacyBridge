package dev.legacybridge.validation.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/** Controlled library interleavings, NOT proof that Minecraft took either schedule. */
@Timeout(30)
final class HandshakeSelectorSnapshotTest {
    @Test void connectSuccessDoesNotGuaranteeActivationHandlerCompleted() throws Exception {
        var group = new NioEventLoopGroup(1);
        Channel channel = null;
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var initialized = new java.util.concurrent.atomic.AtomicBoolean();
        try (var server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            var future = new Bootstrap().group(group).channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override public void channelActive(io.netty.channel.ChannelHandlerContext context) throws Exception {
                            entered.countDown();
                            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Owned activation gate deadline");
                            initialized.set(true);
                            context.fireChannelActive();
                        }
                    }).connect(server.getLocalAddress());
            channel = future.channel();
            assertTrue(future.await(5, TimeUnit.SECONDS));
            assertTrue(future.isSuccess());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            // Pinned Netty fulfills its connect promise BEFORE firing channelActive.
            // This gate proves the library boundary only, not a Minecraft failure.
            assertFalse(initialized.get());
            release.countDown();
            assertTrue(channel.eventLoop().submit(initialized::get).get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            if (channel != null) channel.close().syncUninterruptibly();
            assertTrue(group.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(5, TimeUnit.SECONDS));
        }
    }
    @Test void sameLoopDisableThenEnableRetainsReadInterest() throws Exception { scenario(false); }
    @Test void queuedStaleClearCanLeaveAutoReadTrueWithoutReadInterest() throws Exception { scenario(true); }
    @Test void laterQueuedProtocolEnableDoesNotRestoreStaleReadInterest() throws Exception { scenario(true, true); }
    private void scenario(boolean staleClear) throws Exception {
        scenario(staleClear, false);
    }
    private void scenario(boolean staleClear, boolean laterEnable) throws Exception {
        var group = new NioEventLoopGroup(1);
        Channel channel = null;
        CountDownLatch release = new CountDownLatch(1);
        try (var server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            channel = new Bootstrap().group(group).channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter()).connect(server.getLocalAddress()).sync().channel();
            try (var peer = server.accept()) {
                final Channel owned = channel;
                assertTrue(owned.eventLoop().submit(() -> HandshakeSelectorSnapshot.capture(owned)).get(5, TimeUnit.SECONDS).get("readInterested").getAsBoolean());
                if (staleClear) {
                    CountDownLatch entered = new CountDownLatch(1);
                    var gate = owned.eventLoop().submit(() -> {
                        entered.countDown();
                        if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Owned test gate deadline");
                        return null;
                    });
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    // Controlled schedule: enable is queued, off-loop disable queues clear behind it.
                    owned.eventLoop().execute(() -> owned.config().setAutoRead(true));
                    owned.config().setAutoRead(false);
                    release.countDown(); gate.get(5, TimeUnit.SECONDS);
                    // Source-backed next step: Connection.doSendPacket schedules another
                    // setAutoRead(true). Already-true config makes this a no-op, not a repair.
                    if (laterEnable) owned.eventLoop().submit(() -> owned.config().setAutoRead(true)).get(5, TimeUnit.SECONDS);
                } else {
                    owned.eventLoop().submit(() -> { owned.config().setAutoRead(false); owned.config().setAutoRead(true); }).get(5, TimeUnit.SECONDS);
                }
                var state = owned.eventLoop().submit(() -> HandshakeSelectorSnapshot.capture(owned)).get(5, TimeUnit.SECONDS);
                assertTrue(state.get("autoRead").getAsBoolean());
                assertEquals(!staleClear, state.get("readInterested").getAsBoolean());
                assertEquals(staleClear ? 0 : 1, state.get("interestOps").getAsInt());
                assertThrows(IllegalStateException.class, () -> HandshakeSelectorSnapshot.capture(owned));
            }
        } finally {
            release.countDown();
            if (channel != null) channel.close().syncUninterruptibly();
            assertTrue(group.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(5, TimeUnit.SECONDS));
        }
    }
}
