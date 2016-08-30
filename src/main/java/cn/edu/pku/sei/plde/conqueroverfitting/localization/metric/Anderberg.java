package cn.edu.pku.sei.plde.conqueroverfitting.localization.metric;

/**
 * Created by spirals on 24/07/15.
 */
public class Anderberg implements Metric {

    public double value(int ef, int ep, int nf, int np) {
        // ef / float(ef + ep + nf)
        return ef / ((double) (ef + 2 * (ep + nf)));
    }
}
