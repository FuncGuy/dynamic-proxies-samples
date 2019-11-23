/*
 * Copyright (C) 2000-2019 Heinz Max Kabutz
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Heinz Max Kabutz
 * licenses this file to you under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */

package eu.javaspecialists.books.dynamicproxies.ch03.enhancedstream;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * Described in The Java Specialists Newsletters 274 and 275
 * https://www.javaspecialists.eu/archive/Issue274.html
 * https://www.javaspecialists.eu/archive/Issue275.html
 */
// tag::listing[]
public class EnhancedStreamHandler<T>
    implements InvocationHandler {
  private static final class Key<E> {
    private final E e;
    private final ToIntFunction<E> hashCode;
    private final BiPredicate<E, E> equals;

    public Key(E e, ToIntFunction<E> hashCode,
               BiPredicate<E, E> equals) {
      this.e = e;
      this.hashCode = hashCode;
      this.equals = equals;
    }

    @Override
    public int hashCode() {
      return hashCode.applyAsInt(e);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Key)) return false;
      @SuppressWarnings("unchecked")
      Key<E> that = (Key<E>) obj;
      return equals.test(this.e, that.e);
    }
  }

  private Stream<T> delegate;

  public EnhancedStreamHandler(Stream<T> delegate) {
    this.delegate = delegate;
  }

  private static final Method enhancedDistinct;

  static {
    try {
      enhancedDistinct = EnhancedStream.class.getMethod(
          "distinct", ToIntFunction.class, BiPredicate.class,
          BinaryOperator.class
      );
    } catch (NoSuchMethodException e) {
      throw new Error(e);
    }
  }

  private static final Map<Method, Method> methodMap =
      Stream.of(EnhancedStream.class.getMethods())
          .filter(m -> !m.equals(enhancedDistinct))
          .filter(m -> !Modifier.isStatic(m.getModifiers()))
          .collect(Collectors.toUnmodifiableMap(
              Function.identity(),
              m -> {
                try {
                  return Stream.class.getMethod(
                      m.getName(), m.getParameterTypes());
                } catch (NoSuchMethodException e) {
                  throw new Error(e);
                }
              }));

  @Override
  public Object invoke(Object proxy, Method method,
                       Object[] args) throws Throwable {
    if (method.equals(enhancedDistinct)) {
      return distinct(
          (EnhancedStream<T>) proxy,
          (ToIntFunction<T>) args[0],
          (BiPredicate<T, T>) args[1],
          (BinaryOperator<T>) args[2]);
    } else if (method.getReturnType() == EnhancedStream.class) {
      Method match = methodMap.get(method);
      this.delegate = (Stream) match.invoke(delegate, args);
      return proxy;
    } else {
      return method.invoke(this.delegate, args);
    }
  }

  private EnhancedStream<T> distinct(EnhancedStream<T> proxy,
                                     ToIntFunction<T> hashCode,
                                     BiPredicate<T, T> equals,
                                     BinaryOperator<T> merger) {
    delegate = delegate.collect(Collectors.toMap(
        t -> new Key<>(t, hashCode, equals),
        Function.identity(),
        merger,
        LinkedHashMap::new))
        .values()
        .stream();
    return proxy;
  }
}
// end::listing[]