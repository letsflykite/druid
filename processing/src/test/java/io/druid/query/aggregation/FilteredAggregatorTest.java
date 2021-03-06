/*
 * Druid - a distributed column store.
 * Copyright (C) 2014  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package io.druid.query.aggregation;

import com.google.common.collect.Lists;
import io.druid.query.filter.AndDimFilter;
import io.druid.query.filter.DimFilter;
import io.druid.query.filter.NotDimFilter;
import io.druid.query.filter.OrDimFilter;
import io.druid.query.filter.SelectorDimFilter;
import io.druid.segment.ColumnSelectorFactory;
import io.druid.segment.DimensionSelector;
import io.druid.segment.FloatColumnSelector;
import io.druid.segment.LongColumnSelector;
import io.druid.segment.ObjectColumnSelector;
import io.druid.segment.data.ArrayBasedIndexedInts;
import io.druid.segment.data.IndexedInts;
import org.junit.Assert;
import org.junit.Test;

public class FilteredAggregatorTest
{
  private void aggregate(TestFloatColumnSelector selector, FilteredAggregator agg)
  {
    agg.aggregate();
    selector.increment();
  }

  @Test
  public void testAggregate()
  {
    final float[] values = {0.15f, 0.27f};
    final TestFloatColumnSelector selector = new TestFloatColumnSelector(values);

    FilteredAggregatorFactory factory = new FilteredAggregatorFactory(
        new DoubleSumAggregatorFactory("billy", "value"),
        new SelectorDimFilter("dim", "a")
    );

    FilteredAggregator agg = (FilteredAggregator) factory.factorize(
     makeColumnSelector(selector)
    );

    Assert.assertEquals("billy", agg.getName());

    double expectedFirst = new Float(values[0]).doubleValue();
    double expectedSecond = new Float(values[1]).doubleValue() + expectedFirst;
    double expectedThird = expectedSecond;

    assertValues(agg, selector, expectedFirst, expectedSecond, expectedThird);
  }

  private ColumnSelectorFactory makeColumnSelector(final TestFloatColumnSelector selector){

    return new ColumnSelectorFactory()
    {
      @Override
      public DimensionSelector makeDimensionSelector(String dimensionName)
      {
        if (dimensionName.equals("dim")) {
          return new DimensionSelector()
          {
            @Override
            public IndexedInts getRow()
            {
              if (selector.getIndex() % 3 == 2) {
                return new ArrayBasedIndexedInts(new int[]{1});
              } else {
                return new ArrayBasedIndexedInts(new int[]{0});
              }
            }

            @Override
            public int getValueCardinality()
            {
              return 2;
            }

            @Override
            public String lookupName(int id)
            {
              switch (id) {
                case 0:
                  return "a";
                case 1:
                  return "b";
                default:
                  throw new IllegalArgumentException();
              }
            }

            @Override
            public int lookupId(String name)
            {
              switch (name) {
                case "a":
                  return 0;
                case "b":
                  return 1;
                default:
                  throw new IllegalArgumentException();
              }
            }
          };
        } else {
          throw new UnsupportedOperationException();
        }
      }

      @Override
      public LongColumnSelector makeLongColumnSelector(String columnName)
      {
        throw new UnsupportedOperationException();
      }

      @Override
      public FloatColumnSelector makeFloatColumnSelector(String columnName)
      {
        if (columnName.equals("value")) {
          return selector;
        } else {
          throw new UnsupportedOperationException();
        }
      }

      @Override
      public ObjectColumnSelector makeObjectColumnSelector(String columnName)
      {
        throw new UnsupportedOperationException();
      }
    };
  }

  private void assertValues(FilteredAggregator agg,TestFloatColumnSelector selector, double... expectedVals){
    Assert.assertEquals(0.0d, agg.get());
    Assert.assertEquals(0.0d, agg.get());
    Assert.assertEquals(0.0d, agg.get());
    for(double expectedVal : expectedVals){
      aggregate(selector, agg);
      Assert.assertEquals(expectedVal, agg.get());
      Assert.assertEquals(expectedVal, agg.get());
      Assert.assertEquals(expectedVal, agg.get());
    }
  }

  @Test
  public void testAggregateWithNotFilter()
  {
    final float[] values = {0.15f, 0.27f};
    final TestFloatColumnSelector selector = new TestFloatColumnSelector(values);

    FilteredAggregatorFactory factory = new FilteredAggregatorFactory(
        new DoubleSumAggregatorFactory("billy", "value"),
        new NotDimFilter(new SelectorDimFilter("dim", "b"))
    );

    FilteredAggregator agg = (FilteredAggregator) factory.factorize(
        makeColumnSelector(selector)
    );

    Assert.assertEquals("billy", agg.getName());

    double expectedFirst = new Float(values[0]).doubleValue();
    double expectedSecond = new Float(values[1]).doubleValue() + expectedFirst;
    double expectedThird = expectedSecond;
    assertValues(agg, selector, expectedFirst, expectedSecond, expectedThird);
  }

  @Test
  public void testAggregateWithOrFilter()
  {
    final float[] values = {0.15f, 0.27f, 0.14f};
    final TestFloatColumnSelector selector = new TestFloatColumnSelector(values);

    FilteredAggregatorFactory factory = new FilteredAggregatorFactory(
        new DoubleSumAggregatorFactory("billy", "value"),
        new OrDimFilter(Lists.<DimFilter>newArrayList(new SelectorDimFilter("dim", "a"), new SelectorDimFilter("dim", "b")))
    );

    FilteredAggregator agg = (FilteredAggregator) factory.factorize(
        makeColumnSelector(selector)
    );

    Assert.assertEquals("billy", agg.getName());

    double expectedFirst = new Float(values[0]).doubleValue();
    double expectedSecond = new Float(values[1]).doubleValue() + expectedFirst;
    double expectedThird = expectedSecond + new Float(values[2]).doubleValue();
    assertValues(agg, selector, expectedFirst, expectedSecond, expectedThird);
  }

  @Test
  public void testAggregateWithAndFilter()
  {
    final float[] values = {0.15f, 0.27f};
    final TestFloatColumnSelector selector = new TestFloatColumnSelector(values);

    FilteredAggregatorFactory factory = new FilteredAggregatorFactory(
        new DoubleSumAggregatorFactory("billy", "value"),
        new AndDimFilter(Lists.<DimFilter>newArrayList(new NotDimFilter(new SelectorDimFilter("dim", "b")), new SelectorDimFilter("dim", "a"))));

    FilteredAggregator agg = (FilteredAggregator) factory.factorize(
        makeColumnSelector(selector)
    );

    Assert.assertEquals("billy", agg.getName());

    double expectedFirst = new Float(values[0]).doubleValue();
    double expectedSecond = new Float(values[1]).doubleValue() + expectedFirst;
    double expectedThird = expectedSecond;
    assertValues(agg, selector, expectedFirst, expectedSecond, expectedThird);
  }

}
