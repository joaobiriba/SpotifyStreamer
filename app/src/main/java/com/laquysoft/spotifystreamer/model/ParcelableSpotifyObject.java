package com.laquysoft.spotifystreamer.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by joaobiriba on 16/06/15.
 */
public class ParcelableSpotifyObject implements Parcelable {

    public String mName;
    public String mFatherName;
    public String largeThumbnailUrl;
    public String smallThumbnailUrl;
    public String previewUrl;

    public ParcelableSpotifyObject(String name, String mFatherName, String largeThumbnailUrl,
                                   String smallThumbnailUrl, String previewUrl) {
        this.mName = name;
        this.mFatherName = mFatherName;
        this.largeThumbnailUrl = largeThumbnailUrl;
        this.smallThumbnailUrl = smallThumbnailUrl;
        this.previewUrl = previewUrl;
    }

    private ParcelableSpotifyObject(Parcel in) {
        mName = in.readString();
        mFatherName = in.readString();
        largeThumbnailUrl = in.readString();
        smallThumbnailUrl = in.readString();
        previewUrl = in.readString();
    }


    public static final Creator<ParcelableSpotifyObject> CREATOR = new Creator<ParcelableSpotifyObject>() {
        @Override
        public ParcelableSpotifyObject createFromParcel(Parcel in) {
            return new ParcelableSpotifyObject(in);
        }

        @Override
        public ParcelableSpotifyObject[] newArray(int size) {
            return new ParcelableSpotifyObject[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mName);
        dest.writeString(this.mFatherName);
        dest.writeString(this.largeThumbnailUrl);
        dest.writeString(this.smallThumbnailUrl);
        dest.writeString(this.previewUrl);
    }
}