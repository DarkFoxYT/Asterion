package net.krodark.asterion.port.legacy.network;
import java.util.function.*;
public interface StreamCodec<B,V> {
 V decode(B buffer);
 void encode(B buffer,V value);
 static <B,V> StreamCodec<B,V> of(BiConsumer<B,V> write, Function<B,V> read) {
  return new StreamCodec<>() { public V decode(B b){return read.apply(b);} public void encode(B b,V v){write.accept(b,v);} };
 }
 static <B,V> StreamCodec<B,V> unit(V value) { return of((b,v)-> { if (!value.equals(v)) throw new IllegalArgumentException("Unexpected stateless payload"); }, b->value); }
 static <B,A,V> StreamCodec<B,V> composite(StreamCodec<? super B,A> a,Function<V,A> get,Function<A,V> make) { return of((b,v)->a.encode(b,get.apply(v)), b->make.apply(a.decode(b))); }
 static <B,A,C,V> StreamCodec<B,V> composite(StreamCodec<? super B,A> a,Function<V,A> ga,StreamCodec<? super B,C> c,Function<V,C> gc,BiFunction<A,C,V> make) { return of((b,v)->{a.encode(b,ga.apply(v));c.encode(b,gc.apply(v));}, b->make.apply(a.decode(b),c.decode(b))); }
 default <R> StreamCodec<B,R> map(Function<V,R> read,Function<R,V> write){ return of((b,v)->encode(b,write.apply(v)),b->read.apply(decode(b))); }
 interface Make4<A,C,D,E,V>{V apply(A a,C c,D d,E e);}
 static <B,A,C,D,E,V> StreamCodec<B,V> composite(StreamCodec<? super B,A> a,Function<V,A> ga,StreamCodec<? super B,C> c,Function<V,C> gc,StreamCodec<? super B,D> d,Function<V,D> gd,StreamCodec<? super B,E> e,Function<V,E> ge,Make4<A,C,D,E,V> make){return of((b,v)->{a.encode(b,ga.apply(v));c.encode(b,gc.apply(v));d.encode(b,gd.apply(v));e.encode(b,ge.apply(v));},b->make.apply(a.decode(b),c.decode(b),d.decode(b),e.decode(b)));}
}
