package com.d0klabs.cryptowalt.data;

import java.util.Comparator;

public final class TypeComparator implements Comparator {
    public static boolean DEBUG = false;
    private EditorContext context;

    private static void db(String s) {
        if (DEBUG) {
            System.out.println(s);
        }

    }

    public TypeComparator(EditorContext context) {
        this.context = context;
    }

    public int compare(Object o1, Object o2) {
        Type t1 = (Type) o1;
        Type t2 = (Type) o2;
        db("Comparing " + t1 + " to " + t2);
        EditorContext.ClassHierarchy hier = this.context.getHierarchy();
        if (hier.subclassOf(t1, t2)) {
            db("  " + t1 + " is a subclass of " + t2);
            return -1;
        } else if (hier.subclassOf(t2, t1)) {
            db("  " + t2 + " is a subclass of " + t1);
            return 1;
        } else {
            db("  " + t1 + " and " + t2 + " are unrelated");
            return 1;
        }
    }

    public boolean equals(Object other) {
        return other instanceof TypeComparator;
    }
}
