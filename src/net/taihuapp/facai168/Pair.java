package net.taihuapp.facai168;

/**
 * Created by ghe on 3/11/17.
 */
public class Pair<F, S> {
    private F mFirst;
    private S mSecond;

    public Pair(F f, S s) {
        mFirst = f;
        mSecond = s;
    }

    public F getFirst() { return mFirst; }
    public S getSecond() { return mSecond; }
}
