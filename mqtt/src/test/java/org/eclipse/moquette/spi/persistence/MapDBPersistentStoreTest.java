package org.eclipse.moquette.spi.persistence;

import java.util.Collection;
import java.util.List;

import org.eclipse.moquette.proto.messages.AbstractMessage.QOSType;
import org.eclipse.moquette.spi.IMatchingCondition;
import org.eclipse.moquette.spi.IMessagesStore.StoredMessage;
import org.eclipse.moquette.spi.impl.events.PublishEvent;
import org.eclipse.moquette.spi.impl.subscriptions.Subscription;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MapDBPersistentStoreTest {

	private MapDBPersistentStore store;

	@Before
	public void setUp() {
		store = new MapDBPersistentStore();
		store.initStore();
	}

	@After
	public void tearDown() {
		store.close();
	}

	@Test
	public void retainedMessagesAreSearchable() {
		store.storeRetained("sensors/kitchen/temp", "21C".getBytes(), QOSType.MOST_ONE);
		store.storeRetained("alerts/fire", "warn".getBytes(), QOSType.LEAST_ONE);

		IMatchingCondition sensorsOnly = new IMatchingCondition() {
			@Override
			public boolean match(String key) {
				return key.startsWith("sensors/");
			}
		};

		Collection<StoredMessage> matches = store.searchMatching(sensorsOnly);
		Assert.assertEquals(1, matches.size());
		StoredMessage stored = matches.iterator().next();
		Assert.assertEquals("sensors/kitchen/temp", stored.getTopic());
		Assert.assertEquals(QOSType.MOST_ONE, stored.getQos());
		Assert.assertArrayEquals("21C".getBytes(), stored.getPayload());
	}

	@Test
	public void qos2MessagesRoundTrip() {
		PublishEvent event = new PublishEvent("alerts/fire", QOSType.EXACTLY_ONCE, "boom".getBytes(), true, "client-1", 7);
		store.persistQoS2Message("client-1:7", event);

		PublishEvent restored = store.retrieveQoS2Message("client-1:7");
		Assert.assertEquals("alerts/fire", restored.getTopic());
		Assert.assertEquals(QOSType.EXACTLY_ONCE, restored.getQos());
		Assert.assertArrayEquals("boom".getBytes(), restored.getMessage());
		Assert.assertTrue(restored.isRetain());
		Assert.assertEquals("client-1", restored.getClientID());
		Assert.assertEquals(7, restored.getMessageID());
	}

	@Test
	public void sessionMessagesArePersisted() {
		PublishEvent event = new PublishEvent("sensors/garage/temp", QOSType.LEAST_ONE, "18C".getBytes(), false, "client-9", 3);
		store.storePublishForFuture(event);

		List<PublishEvent> pending = store.listMessagesInSession("client-9");
		Assert.assertEquals(1, pending.size());
		Assert.assertEquals("sensors/garage/temp", pending.get(0).getTopic());
		Assert.assertArrayEquals("18C".getBytes(), pending.get(0).getMessage());
	}

	@Test
	public void nextPacketIdIsPerClient() {
		Assert.assertEquals(1, store.nextPacketID("client-a"));
		Assert.assertEquals(2, store.nextPacketID("client-a"));
		Assert.assertEquals(1, store.nextPacketID("client-b"));
	}

	@Test
	public void subscriptionsAreTracked() {
		Subscription first = new Subscription("client-a", "alerts/#", QOSType.MOST_ONE, true);
		Subscription second = new Subscription("client-b", "sensors/+/temp", QOSType.LEAST_ONE, false);
		store.addNewSubscription(first, "client-a");
		store.addNewSubscription(second, "client-b");

		List<Subscription> all = store.listAllSubscriptions();
		Assert.assertEquals(2, all.size());
		Assert.assertTrue(all.contains(first));
		Assert.assertTrue(all.contains(second));
	}
}
