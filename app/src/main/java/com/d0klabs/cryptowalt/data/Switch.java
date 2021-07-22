package com.d0klabs.cryptowalt.data;

public class Switch {
    private Label defaultTarget;
    private Label[] targets;
    private int[] values;

    public Switch(Label defaultTarget, Label[] targets, int[] values) {
        this.defaultTarget = defaultTarget;
        this.targets = targets;
        this.values = values;
        this.sort();
        this.uniq();
    }

    public void setDefaultTarget(Label target) {
        this.defaultTarget = target;
    }

    public Label defaultTarget() {
        return this.defaultTarget;
    }

    public Label[] targets() {
        return this.targets;
    }

    public int[] values() {
        return this.values;
    }

    public boolean hasContiguousValues() {
        return this.values.length == this.highValue() - this.lowValue() + 1;
    }

    public int lowValue() {
        return this.values[0];
    }

    public int highValue() {
        return this.values[this.values.length - 1];
    }

    private void sort() {
        this.quicksort(0, this.values.length - 1);
    }

    private void quicksort(int p, int r) {
        if (p < r) {
            int q = this.partition(p, r);
            this.quicksort(p, q);
            this.quicksort(q + 1, r);
        }

    }

    private int partition(int p, int r) {
        int x = this.values[p];
        int i = p - 1;
        int j = r + 1;

        while (true) {
            do {
                --j;
            } while (this.values[j] > x);

            do {
                ++i;
            } while (this.values[i] < x);

            if (i >= j) {
                return j;
            }

            int v = this.values[i];
            this.values[i] = this.values[j];
            this.values[j] = v;
            Label t = this.targets[i];
            this.targets[i] = this.targets[j];
            this.targets[j] = t;
        }
    }

    private void uniq() {
        if (this.values.length != 0) {
            int[] v = new int[this.values.length];
            Label[] t = new Label[this.values.length];
            v[0] = this.values[0];
            t[0] = this.targets[0];
            int j = 1;

            for (int i = 1; i < this.values.length; ++i) {
                if (v[j - 1] != this.values[i]) {
                    v[j] = this.values[i];
                    t[j] = this.targets[i];
                    ++j;
                }
            }

            this.values = new int[j];
            System.arraycopy(v, 0, this.values, 0, j);
            this.targets = new Label[j];
            System.arraycopy(t, 0, this.targets, 0, j);
        }
    }

    public String toString() {
        return this.values.length + " pairs";
    }
}
