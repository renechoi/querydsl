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
package com.querydsl.jpa.impl;

import com.querydsl.core.types.ParamExpression;
import com.querydsl.core.types.ParamNotSetException;
import com.querydsl.core.types.dsl.Param;
import com.querydsl.core.util.MathUtils;
import jakarta.persistence.Parameter;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Map;

/**
 * JPAUtil provides static utility methods for JPA
 *
 * @author tiwe
 */
public final class JPAUtil {

  private JPAUtil() {}

  public static void setConstants(
      Query query, List<Object> constants, Map<ParamExpression<?>, Object> params) {
    var hasParameters = !query.getParameters().isEmpty();

    for (var i = 0; i < constants.size(); i++) {
      var val = constants.get(i);

      if (val instanceof Param) {
        Param<?> param = (Param<?>) val;
        val = params.get(val);
        if (val == null) {
          throw new ParamNotSetException(param);
        }
      }

      if (hasParameters) {
        Parameter parameter = query.getParameter(i + 1);
        var parameterType = parameter != null ? parameter.getParameterType() : null;
        if (parameterType != null && !parameterType.isInstance(val)) {
          if (val instanceof Number number && Number.class.isAssignableFrom(parameterType)) {
            val = MathUtils.cast(number, parameterType);
          }
        }
      }

      query.setParameter(i + 1, val);
    }
  }
}
