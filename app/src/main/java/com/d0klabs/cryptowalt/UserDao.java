package com.d0klabs.cryptowalt;

import android.content.Context;

import com.db4o.User;

public class UserDao extends Db4OGenericDao<User>{

    /**
     * @param ctx
     */
    public UserDao(Context ctx) {
        super(ctx);
    }
}