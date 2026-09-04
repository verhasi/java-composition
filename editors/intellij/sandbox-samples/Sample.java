import java.util.List;

class Sample {
    final List<String> c = null;

    public int size()            -> c.size();
    public boolean isEmpty()     -> c.isEmpty();
    static int max(int a, int b) = Math::max;

    // A standard body must NOT be touched.
    public int normal() {
        int x = 5;
        return x;
    }
}
