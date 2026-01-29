package org.eclipse.moquette.spi.impl.subscriptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.moquette.proto.messages.AbstractMessage.QOSType;
import org.eclipse.moquette.spi.ISessionsStore;
import org.junit.Assert;
import org.junit.Test;

public class SubscriptionsStoreTest {

	private static final class FixedSessionsStore implements ISessionsStore {

		private final List<Subscription> subscriptions;

		private FixedSessionsStore(List<Subscription> subscriptions) {
			this.subscriptions = subscriptions;
		}

		@Override
		public void addNewSubscription(Subscription newSubscription, String clientID) {
			throw new UnsupportedOperationException("Not used by this test");
		}

		@Override
		public void wipeSubscriptions(String sessionID) {
			throw new UnsupportedOperationException("Not used by this test");
		}

		@Override
		public void updateSubscriptions(String clientID, Set<Subscription> subscriptions) {
			throw new UnsupportedOperationException("Not used by this test");
		}

		@Override
		public List<Subscription> listAllSubscriptions() {
			return subscriptions;
		}

		@Override
		public boolean contains(String clientID) {
			return false;
		}
	}

	@Test
	public void matchTopicsHandlesWildcards() {
		Assert.assertTrue(SubscriptionsStore.matchTopics("sport/tennis/player1", "sport/tennis/#"));
		Assert.assertTrue(SubscriptionsStore.matchTopics("sport/tennis/player1", "sport/+/player1"));
		Assert.assertTrue(SubscriptionsStore.matchTopics("sport/tennis", "sport/+"));
		Assert.assertTrue(SubscriptionsStore.matchTopics("sport", "sport/#"));
		Assert.assertFalse(SubscriptionsStore.matchTopics("sport/tennis/player1", "sport/+/player2"));
		Assert.assertFalse(SubscriptionsStore.matchTopics("sport/tennis/player1", "sport/tennis"));
	}

	@Test
	public void matchesReturnsAllMatchingSubscriptions() {
		List<Subscription> persisted = new ArrayList<>();
		Subscription exact = new Subscription("client-a", "sensors/kitchen/temp", QOSType.LEAST_ONE, false);
		Subscription wildcard = new Subscription("client-b", "sensors/+/temp", QOSType.MOST_ONE, true);
		Subscription multi = new Subscription("client-c", "alerts/#", QOSType.MOST_ONE, true);
		persisted.add(exact);
		persisted.add(wildcard);
		persisted.add(multi);

		SubscriptionsStore store = new SubscriptionsStore();
		store.init(new FixedSessionsStore(persisted));

		List<Subscription> matches = store.matches("sensors/kitchen/temp");
		Assert.assertEquals(2, matches.size());
		Assert.assertTrue(matches.contains(exact));
		Assert.assertTrue(matches.contains(wildcard));
	}
}
