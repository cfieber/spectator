/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.api;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class CompositeRegistryTest {

  private final ManualClock clock = new ManualClock();

  private Registry newRegistry(int n) {
    CompositeRegistry registry = new CompositeRegistry(clock);
    for (int i = 0; i < n; ++i) {
      registry.add(new DefaultRegistry(clock));
    }
    return registry;
  }

  @Before
  public void init() {
    System.setProperty("spectator.api.propagateWarnings", "true");
    System.setProperty("spectator.api.maxNumberOfMeters", "10000");
  }

  @Test
  public void testCreateId() {
    Registry r = newRegistry(5);
    Assert.assertEquals(r.createId("foo"), new DefaultId("foo"));
  }

  @Test
  public void testCreateIdWithTags() {
    Registry r = newRegistry(5);
    TagList ts = new TagList("k", "v");
    Assert.assertEquals(r.createId("foo", ts), new DefaultId("foo", ts));
  }

  @Test
  public void testRegister() {
    Registry r = newRegistry(5);
    Counter c = new DefaultCounter(clock, r.createId("foo"));
    r.register(c);
    c.increment();
    Assert.assertEquals(c.count(), 1L);
    r.register(c);
    Meter meter = r.get(c.id());
    for (Measurement m : meter.measure()) {
      Assert.assertEquals(m.value(), 2.0, 1e-12);
    }
  }

  @Test
  public void testCounter() {
    Registry r = newRegistry(5);
    Counter c = r.counter(r.createId("foo"));
    c.increment();
    Assert.assertEquals(c.count(), 1L);

    Counter c2 = r.counter(r.createId("foo"));
    Assert.assertEquals(c.count(), c2.count());
  }

  @Test
  public void testTimer() {
    Registry r = newRegistry(5);
    Timer t = r.timer(r.createId("foo"));
    t.record(42L, TimeUnit.MILLISECONDS);
    Assert.assertEquals(t.count(), 1L);

    Timer t2 = r.timer(r.createId("foo"));
    Assert.assertEquals(t.totalTime(), t2.totalTime());
  }

  @Test
  public void testDistributionSummary() {
    Registry r = newRegistry(5);
    DistributionSummary t = r.distributionSummary(r.createId("foo"));
    t.record(42L);
    Assert.assertEquals(t.count(), 1L);

    DistributionSummary t2 = r.distributionSummary(r.createId("foo"));
    Assert.assertEquals(t.totalAmount(), t2.totalAmount());
  }

  @Test(expected = IllegalStateException.class)
  public void testCounterBadTypeAccess() {
    Registry r = newRegistry(5);
    r.counter(r.createId("foo")).count();
    r.distributionSummary(r.createId("foo")).count();
  }

  @Test(expected = IllegalStateException.class)
  public void testTimerBadTypeAccess() {
    Registry r = newRegistry(5);
    r.timer(r.createId("foo")).count();
    r.counter(r.createId("foo")).count();
  }

  @Test(expected = IllegalStateException.class)
  public void testDistributionSummaryBadTypeAccess() {
    Registry r = newRegistry(5);
    r.distributionSummary(r.createId("foo")).count();
    r.timer(r.createId("foo")).count();
  }

  @Test
  public void testRegisterBadTypeAccessNoThrow() {
    System.setProperty("spectator.api.propagateWarnings", "false");
    Registry r = newRegistry(5);
    Counter c = new DefaultCounter(clock, r.createId("foo"));
    r.counter(c.id());
    r.register(c);
    Assert.assertNotSame(r.get(c.id()), c);
  }

  @Test
  public void testCounterBadTypeAccessNoThrow() {
    System.setProperty("spectator.api.propagateWarnings", "false");
    Registry r = newRegistry(5);
    r.counter(r.createId("foo")).count();
    DistributionSummary ds = r.distributionSummary(r.createId("foo"));
    ds.record(42);
    Assert.assertEquals(ds.count(), 0L);
  }

  @Test
  public void testTimerBadTypeAccessNoThrow() {
    System.setProperty("spectator.api.propagateWarnings", "false");
    Registry r = newRegistry(5);
    r.timer(r.createId("foo")).count();
    Counter c = r.counter(r.createId("foo"));
    c.increment();
    Assert.assertEquals(c.count(), 0L);
  }

  @Test
  public void testDistributionSummaryBadTypeAccessNoThrow() {
    System.setProperty("spectator.api.propagateWarnings", "false");
    Registry r = newRegistry(5);
    r.distributionSummary(r.createId("foo")).count();
    Counter c = r.counter(r.createId("foo"));
    c.increment();
    Assert.assertEquals(c.count(), 0L);
  }

  @Test
  public void testGet() {
    Registry r = newRegistry(5);
    Counter c = r.counter(r.createId("foo"));
    c.increment(42);
    Meter m = r.get(c.id());
    Assert.assertEquals(c.measure().iterator().next(), m.measure().iterator().next());
  }

  @Test
  public void testIteratorEmpty() {
    Registry r = newRegistry(5);
    for (Meter m : r) {
      // There is always one composite in the registry used for gauges.
      if (m.id() != CompositeRegistry.GAUGES_ID) {
        Assert.fail("should be empty, but found " + m.id());
      }
    }
  }

  @Test
  public void testIterator() {
    // We need to increment because forwarding the registration to sub-registries is lazy and
    // will not occur if there is no activity
    Registry r = newRegistry(5);
    r.counter(r.createId("foo")).increment();
    r.counter(r.createId("bar")).increment();

    Set<Id> expected = new HashSet<>();
    expected.add(r.createId("foo"));
    expected.add(r.createId("bar"));
    for (Meter m : r) {
      expected.remove(m.id());
    }
    Assert.assertTrue(expected.isEmpty());
  }

  @Test
  public void testIteratorNoRegistries() {
    Registry r = newRegistry(0);
    r.counter(r.createId("foo")).increment();
    Assert.assertTrue(!r.iterator().hasNext());
  }

  @Test
  public void testAddAndRemove() {
    CompositeRegistry r = new CompositeRegistry(clock);

    Counter c1 = r.counter("id1");
    c1.increment();
    Assert.assertEquals(0, c1.count());

    Registry r1 = new DefaultRegistry(clock);
    r.add(r1);

    c1.increment();
    Assert.assertEquals(1, c1.count());

    Registry r2 = new DefaultRegistry(clock);
    r.add(r2);

    c1.increment();
    Assert.assertEquals(2, r1.counter("id1").count());
    Assert.assertEquals(1, r2.counter("id1").count());

    r.remove(r1);

    c1.increment(5);
    Assert.assertEquals(2, r1.counter("id1").count());
    Assert.assertEquals(6, r2.counter("id1").count());
  }

  @Test
  public void testAddGauges() {
    CompositeRegistry r = new CompositeRegistry(clock);

    Id id = r.createId("id1");
    DefaultCounter c1 = new DefaultCounter(clock, id);
    c1.increment();

    Registry r1 = new DefaultRegistry(clock);
    r.add(r1);

    for (Meter meter : r1) {
      for (Measurement m : meter.measure()) {
        Assert.assertEquals(id, m.id());
      }
    }
  }
}
