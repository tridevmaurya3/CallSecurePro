package com.tridev.callsecurepro;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.tridev.callsecurepro.databinding.ActivityMainBinding;
import com.tridev.callsecurepro.ui.calls.CallsFragment;
import com.tridev.callsecurepro.ui.contacts.ContactsFragment;
import com.tridev.callsecurepro.ui.dial.DialFragment;
import com.tridev.callsecurepro.ui.home.HomeFragment;
import com.tridev.callsecurepro.ui.protection.ProtectionFragment;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_NAVIGATION_ITEM =
            "state_selected_navigation_item";

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applySystemBarInsets();
        setupBottomNavigation(savedInstanceState);
    }

    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
            );
            return windowInsets;
        });
    }

    private void setupBottomNavigation(Bundle savedInstanceState) {
        binding.bottomNavigation.setOnItemSelectedListener(item ->
                openMainSection(item.getItemId())
        );

        binding.bottomNavigation.setOnItemReselectedListener(item -> {
            // Keep the current section visible when its tab is selected again.
        });

        int selectedItemId = R.id.nav_home;

        if (savedInstanceState != null) {
            selectedItemId = savedInstanceState.getInt(
                    STATE_SELECTED_NAVIGATION_ITEM,
                    R.id.nav_home
            );
        }

        binding.bottomNavigation.setSelectedItemId(selectedItemId);
    }

    private boolean openMainSection(int itemId) {
        Fragment fragment;
        String fragmentTag;

        if (itemId == R.id.nav_home) {
            fragment = new HomeFragment();
            fragmentTag = "home";
        } else if (itemId == R.id.nav_calls) {
            fragment = new CallsFragment();
            fragmentTag = "calls";
        } else if (itemId == R.id.nav_dial) {
            fragment = new DialFragment();
            fragmentTag = "dial";
        } else if (itemId == R.id.nav_contacts) {
            fragment = new ContactsFragment();
            fragmentTag = "contacts";
        } else if (itemId == R.id.nav_protection) {
            fragment = new ProtectionFragment();
            fragmentTag = "protection";
        } else {
            return false;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.mainFragmentContainer, fragment, fragmentTag)
                .commit();

        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(
                STATE_SELECTED_NAVIGATION_ITEM,
                binding.bottomNavigation.getSelectedItemId()
        );
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
