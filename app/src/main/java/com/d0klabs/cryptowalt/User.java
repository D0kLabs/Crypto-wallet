package com.d0klabs.cryptowalt;

import java.util.UUID;

public class User {
    String username;
    static UUID id;

    public User(String username, UUID id) {
        this.username = username;
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public static UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public static com.db4o.User randomUser(){
        return new com.db4o.User("User", getId().toString());
    }

}
