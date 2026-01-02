package com.roxy.messengerapp.activities;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.roxy.messengerapp.R;

import java.util.HashMap;


public class MainActivity extends AppCompatActivity {
    FirebaseUser firebaseUser;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment); // ID из activity_main.xml

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();

            // Создаем граф вручную
            NavGraph navGraph = navController.getNavInflater().inflate(R.navigation.nav_auth);

            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                navGraph.setStartDestination(R.id.homeFragment);
            } else {
                navGraph.setStartDestination(R.id.registerFragment);
            }

            // Устанавливаем граф ТОЛЬКО ТАК
            navController.setGraph(navGraph);
        }
    }
    private void status(String status){
        // Инициализируем переменную перед проверкой!
        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();

        if (firebaseUser != null) {
            DatabaseReference reference =
                    FirebaseDatabase.getInstance().getReference("Users").child(firebaseUser.getUid());
            HashMap<String, Object> hashMap = new HashMap<>();
            hashMap.put("status", status);

            // updateChildren создаст поле "status", если его нет у старых юзеров
            reference.updateChildren(hashMap);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        status("online");
    }

    @Override
    protected void onPause() {
        super.onPause();
        status("offline");
    }
}