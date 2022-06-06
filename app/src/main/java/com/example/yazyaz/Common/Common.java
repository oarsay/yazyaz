package com.example.yazyaz.Common;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import androidx.annotation.RequiresApi;

import com.example.yazyaz.Model.UserModel;

import java.util.Random;

public class Common {
    public static final String CHAT_LIST_REFERENCE = "ChatList";
    public static final String CHAT_REFERENCE = "Chat";
    public static final String CHAT_DETAIL_REFERENCE = "Detail";
    public static UserModel currentUser = new UserModel();
    public static final String USER_REFERENCES = "People";
    public static UserModel chatUser = new UserModel();

    public static String generateChatRoomId(String a, String b) {
        if (a.compareTo(b) > 0)
            return a + b;
        else if (a.compareTo(b) < 0)
            return b + a;
        else
            return "Chat_Your_Self_Error" +
                    new Random().nextInt();
    }

    public static String getName(UserModel chatUser) {
        return chatUser.getFirstName() +
                " " +
                chatUser.getLastName();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String getFileName(ContentResolver contentResolver, Uri fileUri) {

        String result = null;
        if(fileUri.getScheme().equals("content")){
            try (Cursor cursor = contentResolver.query(fileUri, null, null, null)) {
                if (cursor != null && cursor.moveToFirst())
                    result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
            }
        }
        if(result == null){
            result = fileUri.getPath();
            int cut = result.lastIndexOf("/");
            if (cut != -1)
                result = result.substring(cut + 1);
        }
        return result;
    }
}
