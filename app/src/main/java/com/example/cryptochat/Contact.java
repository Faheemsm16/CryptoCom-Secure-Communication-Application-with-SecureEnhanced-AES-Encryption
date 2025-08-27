package com.example.cryptochat;

import android.os.Parcel;
import android.os.Parcelable;

public class Contact implements Parcelable {
    private String name;
    private String email;
    private String id;
    private String phoneNumber; // Add this field
    private boolean registered; // Add this field


    public Contact() {
        this.id = "";
        this.registered = false;
    }

    public Contact(String name, String email) {
        this.name = name;
        this.email = email;
        this.id = "";
        this.phoneNumber = phoneNumber;
        this.registered = false;
    }

    public Contact(String name) {
        this.name = name;
        this.email = "";
        this.id = "";
        this.registered = false;
    }

    protected Contact(Parcel in) {
        name = in.readString();
        email = in.readString();
        id = in.readString();
        registered = in.readByte() != 0; // Read boolean from Parcel
    }

    public static final Creator<Contact> CREATOR = new Creator<Contact>() {
        @Override
        public Contact createFromParcel(Parcel in) {
            return new Contact(in);
        }

        @Override
        public Contact[] newArray(int size) {
            return new Contact[size];
        }
    };

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(email);
        dest.writeString(id);
        dest.writeByte((byte) (registered ? 1 : 0)); // Write boolean to Parcel
    }
}
