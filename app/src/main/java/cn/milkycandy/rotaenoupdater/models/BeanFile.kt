package cn.milkycandy.rotaenoupdater.models

import android.os.Parcel
import android.os.Parcelable

data class BeanFile(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val isGrantedPath: Boolean,
    val pathPackageName: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(path)
        parcel.writeByte(if (isDir) 1 else 0)
        parcel.writeByte(if (isGrantedPath) 1 else 0)
        parcel.writeString(pathPackageName)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<BeanFile> {
        override fun createFromParcel(parcel: Parcel): BeanFile {
            return BeanFile(parcel)
        }

        override fun newArray(size: Int): Array<BeanFile?> {
            return arrayOfNulls(size)
        }
    }
}