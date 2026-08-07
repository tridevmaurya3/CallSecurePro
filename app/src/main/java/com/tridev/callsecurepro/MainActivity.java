package com.tridev.callsecurepro;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
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

    /**
     * Keeps page content clear of status bars and display cut-outs while allowing the
     * bottom navigation surface to extend behind the system navigation area. The bottom
     * inset is applied only inside the navigation surface, so icons and labels are never
     * compressed or clipped on gesture-navigation and three-button-navigation devices.
     */
    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );

            view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    0
            );

            binding.bottomNavigationSurface.setPadding(
                    0,
                    0,
                    0,
                    systemBars.bottom
            );

            return windowInsets;
        });

        ViewCompat.requestApplyInsets(binding.main);
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

        MenuItem selectedItem = binding.bottomNavigation.getMenu().findItem(selectedItemId);
        if (selectedItem != null) {
            selectedItem.setChecked(true);
        }

        Fragment restoredFragment = getSupportFragmentManager()
                .findFragmentById(R.id.mainFragmentContainer);

        if (savedInstanceState == null || restoredFragment == null) {
            openMainSection(selectedItemId);
        }
    }

    /**
     * Lets dashboard actions open one of the five primary sections through the same
     * BottomNavigationView state used by direct tab touches.
     */
    public void selectMainSection(@IdRes int itemId) {
        if (binding != null) {
            binding.bottomNavigation.setSelectedItemId(itemId);
        }
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
