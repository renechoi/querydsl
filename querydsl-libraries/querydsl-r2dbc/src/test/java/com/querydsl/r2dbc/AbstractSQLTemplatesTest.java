/*
 * Copyright 2015, The Querydsl Team (http://www.querydsl.com/team)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.querydsl.r2dbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.CollectionExpression;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Operator;
import com.querydsl.core.types.Ops;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.TemplatesTestUtils;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.r2dbc.domain.QSurvey;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractSQLTemplatesTest {

  protected static final QSurvey survey1 = new QSurvey("survey1");

  protected static final QSurvey survey2 = new QSurvey("survey2");

  private SQLTemplates templates;

  protected R2DBCQuery<?> query;

  protected abstract SQLTemplates createTemplates();

  @Before
  public void setUp() {
    templates = createTemplates();
    templates.newLineToSingleSpace();
    query = new R2DBCQuery<Void>(new Configuration(templates));
  }

  @Test
  public void noFrom() {
    query.getMetadata().setProjection(Expressions.ONE);
    if (templates.getDummyTable() == null) {
      assertThat(query).hasToString("select 1");
    } else {
      assertThat(query).hasToString("select 1 from " + templates.getDummyTable());
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void union() {
    var one = Expressions.ONE;
    var two = Expressions.TWO;
    var three = Expressions.THREE;
    Path<Integer> col1 = Expressions.path(Integer.class, "col1");
    Union union =
        query.union(
            R2DBCExpressions.select(one.as(col1)),
            R2DBCExpressions.select(two),
            R2DBCExpressions.select(three));

    if (templates.getDummyTable() == null) {
      if (templates.isUnionsWrapped()) {
        assertThat(union.toString())
            .isEqualTo(
                """
                (select 1 as col1)
                union
                (select 2)
                union
                (select 3)\
                """);
      } else {
        assertThat(union.toString())
            .isEqualTo("""
				select 1 as col1)
				union
				select 2
				union
				select 3""");
      }
    } else {
      var dummyTable = templates.getDummyTable();
      if (templates.isUnionsWrapped()) {
        assertThat(union.toString())
            .isEqualTo(
                "(select 1 as col1 from "
                    + dummyTable
                    + ")\n"
                    + "union\n"
                    + "(select 2 from "
                    + dummyTable
                    + ")\n"
                    + "union\n"
                    + "(select 3 from "
                    + dummyTable
                    + ")");
      } else {
        assertThat(union.toString())
            .isEqualTo(
                "select 1 as col1 from "
                    + dummyTable
                    + "\n"
                    + "union\n"
                    + "select 2 from "
                    + dummyTable
                    + "\n"
                    + "union\n"
                    + "select 3 from "
                    + dummyTable);
      }
    }
  }

  @Test
  public void innerJoin() {
    query.from(survey1).innerJoin(survey2);
    assertThat(query).hasToString("from SURVEY survey1 inner join SURVEY survey2");
  }

  protected int getPrecedence(Operator... ops) {
    var precedence = templates.getPrecedence(ops[0]);
    for (var i = 1; i < ops.length; i++) {
      assertThat(templates.getPrecedence(ops[i])).as(ops[i].name()).isEqualTo(precedence);
    }
    return precedence;
  }

  @Test
  public void generic_precedence() {
    TemplatesTestUtils.testPrecedence(templates);
  }

  @Test
  public void arithmetic() {
    NumberExpression<Integer> one = Expressions.numberPath(Integer.class, "one");
    NumberExpression<Integer> two = Expressions.numberPath(Integer.class, "two");

    // add
    assertSerialized(one.add(two), "one + two");
    assertSerialized(one.add(two).multiply(1), "(one + two) * ?");
    assertSerialized(one.add(two).divide(1), "(one + two) / ?");
    assertSerialized(one.add(two).add(1), "one + two + ?");

    assertSerialized(one.add(two.multiply(1)), "one + two * ?");
    assertSerialized(one.add(two.divide(1)), "one + two / ?");
    assertSerialized(one.add(two.add(1)), "one + (two + ?)"); // XXX could be better

    // sub
    assertSerialized(one.subtract(two), "one - two");
    assertSerialized(one.subtract(two).multiply(1), "(one - two) * ?");
    assertSerialized(one.subtract(two).divide(1), "(one - two) / ?");
    assertSerialized(one.subtract(two).add(1), "one - two + ?");

    assertSerialized(one.subtract(two.multiply(1)), "one - two * ?");
    assertSerialized(one.subtract(two.divide(1)), "one - two / ?");
    assertSerialized(one.subtract(two.add(1)), "one - (two + ?)");

    // mult
    assertSerialized(one.multiply(two), "one * two");
    assertSerialized(one.multiply(two).multiply(1), "one * two * ?");
    assertSerialized(one.multiply(two).divide(1), "one * two / ?");
    assertSerialized(one.multiply(two).add(1), "one * two + ?");

    assertSerialized(one.multiply(two.multiply(1)), "one * (two * ?)"); // XXX could better
    assertSerialized(one.multiply(two.divide(1)), "one * (two / ?)");
    assertSerialized(one.multiply(two.add(1)), "one * (two + ?)");
  }

  @Test
  public void booleanTemplate() {
    assertSerialized(Expressions.booleanPath("b").eq(Expressions.TRUE), "b = 1");
    assertSerialized(Expressions.booleanPath("b").eq(Expressions.FALSE), "b = 0");
    query.setUseLiterals(true);
    query.where(Expressions.booleanPath("b").eq(true));
    assertThat(query.toString().endsWith("where b = 1")).as(query.toString()).isTrue();
  }

  protected void assertSerialized(Expression<?> expr, String serialized) {
    var serializer = new SQLSerializer(new Configuration(templates));
    serializer.handle(expr);
    assertThat(serializer).hasToString(serialized);
  }

  @Test
  public void in() {
    CollectionExpression<Collection<Integer>, Integer> ints =
        Expressions.collectionOperation(
            Integer.class,
            Ops.LIST,
            Expressions.collectionOperation(
                Integer.class, Ops.LIST, Expressions.ONE, Expressions.TWO),
            Expressions.THREE);
    query.from(survey1).where(survey1.id.in(ints));
    query.getMetadata().setProjection(survey1.name);
    assertThat(query.toString())
        .isEqualTo("select survey1.NAME from SURVEY survey1 where survey1.ID in (1, 2, 3)");
  }
}
