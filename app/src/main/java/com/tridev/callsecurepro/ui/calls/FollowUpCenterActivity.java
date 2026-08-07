package com.tridev.callsecurepro.ui.calls;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.tridev.callsecurepro.databinding.ActivityFollowUpCenterBinding;

public class FollowUpCenterActivity extends AppCompatActivity {

    private ActivityFollowUpCenterBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityFollowUpCenterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.followUpRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.backButton.setOnClickListener(view -> finish());
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
