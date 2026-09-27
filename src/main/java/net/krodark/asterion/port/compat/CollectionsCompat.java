package net.krodark.asterion.port.compat;
public final class CollectionsCompat {
 private CollectionsCompat() {}
 public static <T> T removeFirst(java.util.Set<T> values) {
  var iterator = values.iterator(); T first = iterator.next(); iterator.remove(); return first;
 }
}
