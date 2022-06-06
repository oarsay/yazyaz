package com.example.yazyaz;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.bumptech.glide.Glide;
import com.example.yazyaz.Common.Common;
import com.example.yazyaz.Listener.IFirebaseLoadFailed;
import com.example.yazyaz.Listener.ILoadTimeFromFirebaseListener;
import com.example.yazyaz.Model.ChatInfoModel;
import com.example.yazyaz.Model.ChatMessageModel;
import com.example.yazyaz.ViewHolders.ChatPictureHolder;
import com.example.yazyaz.ViewHolders.ChatPictureReceiveHolder;
import com.example.yazyaz.ViewHolders.ChatTextHolder;
import com.example.yazyaz.ViewHolders.ChatTextReceiveHolder;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class ChatActivity extends AppCompatActivity implements ILoadTimeFromFirebaseListener, IFirebaseLoadFailed {

    private static final int MY_CAMERA_REQUEST_CODE = 7171;
    private static final int MY_RESULT_LOAD_IMAGE = 7172;

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.img_camera)
    ImageView img_camera;
    @BindView(R.id.img_image)
    ImageView img_image;
    @BindView(R.id.img_send)
    ImageView img_send;
    @BindView(R.id.img_preview)
    ImageView img_preview;
    @BindView(R.id.img_avatar)
    ImageView img_avatar;
    @BindView(R.id.edt_chat)
    AppCompatEditText edt_chat;
    @BindView(R.id.recycler_chat)
    RecyclerView recycler_chat;
    @BindView(R.id.txt_name)
    TextView txt_name;

    FirebaseDatabase database;
    DatabaseReference chatRef, offsetRef;
    ILoadTimeFromFirebaseListener listener;
    IFirebaseLoadFailed errorListener;

    FirebaseRecyclerAdapter<ChatMessageModel, RecyclerView.ViewHolder> adapter;
    FirebaseRecyclerOptions<ChatMessageModel> options;
    Uri fileUri;
    StorageReference storageReference;
    LinearLayoutManager layoutManager;

    @OnClick(R.id.img_image)
    void onSelectImageClick(){
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, MY_RESULT_LOAD_IMAGE);
    }

    @OnClick(R.id.img_camera)
    void onCaptureImageClick(){
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From your camera");
        fileUri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        );
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        startActivityForResult(intent, MY_CAMERA_REQUEST_CODE);
    }

    @OnClick(R.id.img_send)
    void onSubmitChatClick() {
        offsetRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long offset = snapshot.getValue(Long.class);
                long estimatedServerTimeInMs = System.currentTimeMillis() + offset;

                listener.onLoadOnlyTimeSuccess(estimatedServerTimeInMs);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                errorListener.onError(error.getMessage());
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == MY_CAMERA_REQUEST_CODE){
            if(resultCode == RESULT_OK){
                Log.d("REQUEST", "CAMERA");
                try {
                    Bitmap thumbnail = MediaStore.Images.Media
                            .getBitmap(
                                    getContentResolver(),
                                    fileUri
                            );
                    img_preview.setImageBitmap(thumbnail);
                    img_preview.setVisibility(View.VISIBLE);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        else if(requestCode == MY_RESULT_LOAD_IMAGE){
            if(resultCode == RESULT_OK){
                try {
                    Log.d("REQUEST", "GALLERY");
                    final Uri imageUri = data.getData();
                    InputStream inputStream = getContentResolver()
                            .openInputStream(imageUri);
                    Bitmap selectedImage = BitmapFactory.decodeStream(inputStream);
                    img_preview.setImageBitmap(selectedImage);
                    img_preview.setVisibility(View.VISIBLE);
                    fileUri = imageUri;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        else
            Toast.makeText(this, "Please choose an image", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (adapter != null)
            adapter.startListening();
    }

    @Override
    protected void onStop() {
        if (adapter != null) adapter.stopListening();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null)
            adapter.startListening();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        initViews();
        loadChatContent();
    }

    private void loadChatContent() {

        String receiverId = Objects.requireNonNull(FirebaseAuth.getInstance()
                .getCurrentUser()).getUid();

        adapter = new FirebaseRecyclerAdapter<ChatMessageModel, RecyclerView.ViewHolder>(options) {

            @Override
            public int getItemViewType(int position) {
                if (adapter.getItem(position).getSenderId()
                        .equals(receiverId)) // If message is own
                    return !adapter.getItem(position).isPicture() ? 0 : 1;
                else
                    return !adapter.getItem(position).isPicture() ? 2 : 3;
            }

            @Override
            protected void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull ChatMessageModel model) {
                if (holder instanceof ChatTextHolder) {
                    ChatTextHolder chatTextHolder = (ChatTextHolder) holder;
                    chatTextHolder.txt_chat_message.setText(model.getContent());
                    chatTextHolder.txt_time.setText(
                            DateUtils.getRelativeTimeSpanString(model.getTimeStamp(),
                                            Calendar.getInstance().getTimeInMillis(), 0)
                                    .toString());
                } else if (holder instanceof ChatTextReceiveHolder) {
                    ChatTextReceiveHolder chatTextHolder = (ChatTextReceiveHolder) holder;
                    chatTextHolder.txt_chat_message.setText(model.getContent());
                    chatTextHolder.txt_time.setText(
                            DateUtils.getRelativeTimeSpanString(model.getTimeStamp(),
                                            Calendar.getInstance().getTimeInMillis(), 0)
                                    .toString());
                }
                else if (holder instanceof ChatPictureHolder){
                    ChatPictureHolder chatPictureHolder = (ChatPictureHolder) holder;
                    chatPictureHolder.txt_chat_message.setText(model.getContent());
                    chatPictureHolder.txt_time.setText(
                            DateUtils.getRelativeTimeSpanString(model.getTimeStamp(),
                                            Calendar.getInstance().getTimeInMillis(), 0)
                                    .toString());
                    Glide.with(ChatActivity.this)
                            .load(model.getPictureLink())
                            .into(chatPictureHolder.img_preview);
                }
                else if(holder instanceof ChatPictureReceiveHolder){
                    ChatPictureReceiveHolder chatPictureHolder = (ChatPictureReceiveHolder) holder;
                    chatPictureHolder.txt_chat_message.setText(model.getContent());
                    chatPictureHolder.txt_time.setText(
                            DateUtils.getRelativeTimeSpanString(model.getTimeStamp(),
                                            Calendar.getInstance().getTimeInMillis(), 0)
                                    .toString());
                    Glide.with(ChatActivity.this)
                            .load(model.getPictureLink())
                            .into(chatPictureHolder.img_preview);
                }
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                if (viewType == 0) // Text message of user *own message*
                {
                    view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.layout_message_text_own, parent, false);
                    return new ChatTextReceiveHolder(view);
                } else if(viewType == 1) // Picture message of user
                {
                    view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.layout_message_picture_own, parent, false);
                    return new ChatPictureHolder(view);
                }
                else if(viewType == 2) // Text message of friend
                {
                    view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.layout_message_text_friend, parent, false);
                    return new ChatTextHolder(view);
                }
                else{ // Picture message of friend
                    view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.layout_message_picture_friend, parent, false);
                    return new ChatPictureReceiveHolder(view);
                }

            }
        };

        // Auto scroll when receive new message
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int friendlyMessageCount = adapter.getItemCount();
                int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
                if (lastVisiblePosition == -1 ||
                        (positionStart >= (friendlyMessageCount - 1) &&
                                lastVisiblePosition == (positionStart - 1))) {
                    recycler_chat.scrollToPosition(positionStart);
                }
            }
        });
        recycler_chat.setAdapter(adapter);
    }

    private void initViews() {

        listener = this;
        errorListener = this;
        database = FirebaseDatabase.getInstance();
        chatRef = database.getReference(Common.CHAT_REFERENCE);
        offsetRef = database.getReference(".info/serverTimeOffset");

        Query query = chatRef.child(Common.generateChatRoomId(
                Common.chatUser.getUid(),
                Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid()
        )).child(Common.CHAT_DETAIL_REFERENCE);

        options = new FirebaseRecyclerOptions.Builder<ChatMessageModel>()
                .setQuery(query, ChatMessageModel.class).build();
        ButterKnife.bind(this);
        layoutManager = new LinearLayoutManager(this);
        recycler_chat.setLayoutManager(layoutManager);

        ColorGenerator generator = ColorGenerator.MATERIAL;
        int color = generator.getColor(Common.chatUser.getUid());
        TextDrawable.IBuilder builder = TextDrawable.builder()
                .beginConfig()
                .withBorder(4)
                .endConfig()
                .round();

        TextDrawable drawable = builder.build(Common.chatUser.getFirstName().substring(0, 1), color);
        img_avatar.setImageDrawable(drawable);
        txt_name.setText(Common.getName(Common.chatUser));

        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setNavigationOnClickListener(view -> {
            finish();
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onLoadOnlyTimeSuccess(long estimateTimeInMs) {

        ChatMessageModel chatMessageModel = new ChatMessageModel();
        chatMessageModel.setName(Common.getName(Common.currentUser));
        chatMessageModel.setContent(Objects.requireNonNull(edt_chat.getText()).toString());
        chatMessageModel.setTimeStamp(estimateTimeInMs);
        chatMessageModel.setSenderId(Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid());

        if(fileUri == null){
            chatMessageModel.setPicture(false);
            submitChatToFirebase(chatMessageModel, chatMessageModel.isPicture(), estimateTimeInMs);
        }
        else{
            uploadPicture(fileUri, chatMessageModel, estimateTimeInMs);
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void uploadPicture(Uri fileUri, ChatMessageModel chatMessageModel, long estimateTimeInMs) {
        AlertDialog dialog = new AlertDialog.Builder(ChatActivity.this)
                .setCancelable(false)
                .setMessage("Please wait...")
                .create();
        dialog.show();

        String fileName = Common.getFileName(getContentResolver(), fileUri);
        String path = Common.chatUser.getUid() +
                "/" +
                fileName;
        storageReference = FirebaseStorage.getInstance()
                .getReference()
                .child(path);
        Log.d("PATH:", path);
        UploadTask uploadTask = storageReference.putFile(fileUri);
        // Create task
        Task<Uri> task = uploadTask.continueWithTask(task1 -> {
            if(!task1.isSuccessful())
                Toast.makeText(this, "Failed to upload", Toast.LENGTH_SHORT).show();
            return storageReference.getDownloadUrl();
        }).addOnCompleteListener(task12 -> {
            if (task12.isSuccessful()){
                String url = task12.getResult().toString();
                dialog.dismiss();

                chatMessageModel.setPicture(true);
                chatMessageModel.setPictureLink(url);

                submitChatToFirebase(chatMessageModel, chatMessageModel.isPicture(), estimateTimeInMs);

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(ChatActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void submitChatToFirebase(ChatMessageModel chatMessageModel, boolean isPicture, long estimateTimeInMs) {


        chatRef.child(Common.generateChatRoomId(Common.chatUser.getUid(),
                        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid()))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists())
                            appendChat(chatMessageModel, isPicture, estimateTimeInMs);
                        else
                            createChat(chatMessageModel, isPicture, estimateTimeInMs);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ChatActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void appendChat(ChatMessageModel chatMessageModel, boolean isPicture, long estimateTimeInMs) {

        Map<String, Object> update_data = new HashMap<>();
        update_data.put("lastUpdate", estimateTimeInMs);

        if(isPicture)
            update_data.put("lastMessage", "<Image>");
        else
            update_data.put("lastMessage", chatMessageModel.getContent());

        // Update
        // Update on user list
        FirebaseDatabase.getInstance()
                .getReference(Common.CHAT_LIST_REFERENCE)
                .child(Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid())
                .child(Common.chatUser.getUid())
                .updateChildren(update_data)
                .addOnFailureListener(e -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                })
                .addOnSuccessListener(unused -> {
                    // Submit success for ChatInfo
                    // Copy to Friend Chat List
                    FirebaseDatabase.getInstance()
                            .getReference(Common.CHAT_LIST_REFERENCE)
                            .child(Common.chatUser.getUid()) // Swap
                            .child(FirebaseAuth.getInstance().getCurrentUser().getUid()) // Swap
                            .updateChildren(update_data)
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                            })
                            .addOnSuccessListener(unused1 -> {

                                // Add on Chat Ref
                                chatRef.child(Common.generateChatRoomId(Common.chatUser.getUid(),
                                                FirebaseAuth.getInstance().getCurrentUser().getUid()))
                                        .child(Common.CHAT_DETAIL_REFERENCE)
                                        .push()
                                        .setValue(chatMessageModel)
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                Toast.makeText(ChatActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        })
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {

                                                if (task.isSuccessful()) {

                                                    // Clear
                                                    edt_chat.setText("");
                                                    edt_chat.requestFocus();
                                                    if (adapter != null) {
                                                        adapter.notifyDataSetChanged();
                                                    }
                                                    // Clear preview thumbnail
                                                    if(isPicture){
                                                        fileUri = null;
                                                        img_preview.setVisibility(View.GONE);
                                                    }
                                                }
                                            }
                                        });
                            });
                });

    }

    private void createChat(ChatMessageModel chatMessageModel, boolean isPicture, long estimateTimeInMs) {


        ChatInfoModel chatInfoModel = new ChatInfoModel();
        chatInfoModel.setCreateId(Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid());
        chatInfoModel.setFriendName(Common.getName(Common.chatUser));
        chatInfoModel.setFriendId(Common.chatUser.getUid());
        chatInfoModel.setCreateName(Common.getName(Common.currentUser));

        if(isPicture)
            chatInfoModel.setLastMessage("<Image>");
        else
            chatInfoModel.setLastMessage(chatMessageModel.getContent());

        chatInfoModel.setLastUpdate(estimateTimeInMs);
        chatInfoModel.setCreateDate(estimateTimeInMs);

        // Submit on Firebase
        // Add on User Chat List
        FirebaseDatabase.getInstance()
                .getReference(Common.CHAT_LIST_REFERENCE)
                .child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                .child(Common.chatUser.getUid())
                .setValue(chatInfoModel)
                .addOnFailureListener(e -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                })
                .addOnSuccessListener(unused -> {
                    // Submit success for ChatInfo
                    // Copy to Friend Chat List
                    FirebaseDatabase.getInstance()
                            .getReference(Common.CHAT_LIST_REFERENCE)
                            .child(Common.chatUser.getUid()) // Swap
                            .child(FirebaseAuth.getInstance().getCurrentUser().getUid()) // Swap
                            .setValue(chatInfoModel)
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                            })
                            .addOnSuccessListener(unused1 -> {

                                // Add on Chat Ref
                                chatRef.child(Common.generateChatRoomId(Common.chatUser.getUid(),
                                                FirebaseAuth.getInstance().getCurrentUser().getUid()))
                                        .child(Common.CHAT_DETAIL_REFERENCE)
                                        .push()
                                        .setValue(chatMessageModel)
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                Toast.makeText(ChatActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        })
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {

                                                if (task.isSuccessful()) {

                                                    // Clear
                                                    edt_chat.setText("");
                                                    edt_chat.requestFocus();
                                                    if (adapter != null) {
                                                        adapter.notifyDataSetChanged();
                                                    }
                                                    // Clear preview thumbnail
                                                    if(isPicture){
                                                        fileUri = null;
                                                        img_preview.setVisibility(View.GONE);
                                                    }
                                                }
                                            }
                                        });
                            });
                });
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}