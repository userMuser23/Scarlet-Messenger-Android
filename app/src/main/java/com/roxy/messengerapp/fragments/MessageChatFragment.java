package com.roxy.messengerapp.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.roxy.messengerapp.R;
import com.roxy.messengerapp.adapters.MessageAdapter;
import com.roxy.messengerapp.entities.Chat;
import com.roxy.messengerapp.entities.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class MessageChatFragment extends Fragment {

    CircleImageView profile_image;
    TextView username;
    FirebaseUser fuser;
    DatabaseReference reference;

    ImageButton btn_send;
    MessageAdapter messageAdapter;
    List<Chat> mChat;
    RecyclerView recyclerView;
    EditText text_send;

    String userid; // ID собеседника

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_message_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Настройка Toolbar
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        ((AppCompatActivity)getActivity()).setSupportActionBar(toolbar);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle("");
        ((AppCompatActivity)getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        // Делаем так, чтобы сообщения "прилипали" к низу (как в стеке)
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        linearLayoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(linearLayoutManager);

        // Кнопка "Назад" в тулбаре
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Возвращаемся назад
                Navigation.findNavController(v).navigateUp();
            }
        });

        // 2. Инициализация View
        profile_image = view.findViewById(R.id.profile_image);
        username = view.findViewById(R.id.username);
        btn_send = view.findViewById(R.id.btn_send);
        text_send = view.findViewById(R.id.text_send);

        // 3. Получаем ID собеседника из аргументов (Navigation Component)
        if (getArguments() != null) {
            userid = getArguments().getString("userid");
        }

        fuser = FirebaseAuth.getInstance().getCurrentUser();

        // 4. Загружаем инфо о собеседнике, чтобы показать в шапке
        reference = FirebaseDatabase.getInstance().getReference("Users").child(userid);

        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    username.setText(user.getUsername());
                    if (user.getImageURL().equals("default")){
                        profile_image.setImageResource(R.mipmap.ic_launcher);
                    } else {
                        Glide.with(requireContext()).load(user.getImageURL()).into(profile_image);
                    }
                }
                readMessages(fuser.getUid(), userid, user.getImageURL());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = text_send.getText().toString();
                if (!msg.equals("")){
                    sendMessage(fuser.getUid(), userid, msg);
                } else {
                    Toast.makeText(getContext(), "Вы не можете отправить пустое сообщение", Toast.LENGTH_SHORT).show();
                }
                text_send.setText("");
            }
        });
    }
    private void sendMessage(String sender, String receiver, String message) {
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference();

        String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("sender", sender);
        hashMap.put("receiver", receiver);
        hashMap.put("message", message);
        hashMap.put("isseen", false);
        hashMap.put("time", currentTime);

        // Создаем новую ветку в узле "Chats"
        // push() генерирует уникальный ключ сообщения
        reference.child("Chats").push().setValue(hashMap);

        // --- НОВАЯ ЧАСТЬ: Добавляем пользователей в списки чатов друг друга ---

        // 1. Добавляем получателя в список чатов отправителя
        final DatabaseReference chatRef = FirebaseDatabase.getInstance().getReference("Chatlist")
                .child(fuser.getUid())
                .child(userid);

        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()){
                    chatRef.child("id").setValue(userid);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
        });

        // 2. Добавляем отправителя в список чатов получателя (чтобы он тоже увидел диалог)
        final DatabaseReference chatRefReceiver = FirebaseDatabase.getInstance().getReference("Chatlist")
                .child(userid)
                .child(fuser.getUid());

        chatRefReceiver.child("id").setValue(fuser.getUid());
    }

    private void readMessages(final String myid, final String userid, final String imageurl){
        mChat = new ArrayList<>();

        reference = FirebaseDatabase.getInstance().getReference("Chats");
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mChat.clear(); // Чистим список перед обновлением
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    Chat chat = snapshot.getValue(Chat.class);
                    chat.setId(snapshot.getKey());
                    // ФИЛЬТРАЦИЯ (Самое важное!)
                    // Нам нужны сообщения ТОЛЬКО между мной и этим конкретным юзером
                    // 1. Я отправил -> Ему
                    // 2. Он отправил -> Мне
                    if (chat.getReceiver().equals(myid) && chat.getSender().equals(userid) ||
                            chat.getReceiver().equals(userid) && chat.getSender().equals(myid)){
                        mChat.add(chat);
                    }
                }

                messageAdapter = new MessageAdapter(getContext(), mChat, imageurl);
                recyclerView.setAdapter(messageAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }
}