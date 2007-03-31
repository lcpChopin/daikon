package binary_variables;

/**
 * Date: 27/02/2007
 */
public class Addressable<T extends Addressable> implements Comparable<T> {
  public final long address;
  public final String module;

  public Addressable(Addressable addr) {
    address = addr.address;
    module = addr.module;
  }
  public Addressable(long address, String module) {
    this.address = address;
    this.module = module.intern();
  }
  public int compareTo(T o) {
    int res = module.compareTo(o.module);
    return res!=0 ? res :
        address==o.address ? 0 : address>o.address ? 1 : -1;
  }
  public boolean equals(Object o) {
    Addressable b = (Addressable)o;
    return b.address==address && b.module.equals(module);
  }
  public int hashCode() {
    return (int)address;
  }
  public String toString() {
    return getAddr();
  }
  public String getAddr() {
    return module+"_0x"+Long.toHexString(address);
  }
}