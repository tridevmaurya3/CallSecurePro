package com.tridev.callsecurepro;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.tridev.callsecurepro.databinding.ActivityMainBinding;
import com.tridev.callsecurepro.theme.AppVisualThemeManager;
import com.tridev.callsecurepro.theme.ThemePreferences;
import com.tridev.callsecurepro.ui.calls.CallsFragment;
import com.tridev.callsecurepro.ui.contacts.ContactsFragment;
import com.tridev.callsecurepro.ui.dial.DialFragment;
import com.tridev.callsecurepro.ui.home.HomeFragment;
import com.tridev.callsecurepro.ui.protection.ProtectionFragment;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_NAVIGATION_ITEM =
            "state_selected_navigation_item";

    private ActivityMainBinding binding;
    private boolean pendingDialRequest;
    @Nullable
    private String pendingDialNumber;
    @NonNull
    private String appliedThemeSignature = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        appliedThemeSignature = ThemePreferences.signature(this);
        registerFragmentThemeCallbacks();
        captureExternalDialIntent(getIntent());
        applySystemBarInsets();
        setupBottomNavigation(savedInstanceState);
        applyVisualTheme();
    }

    private void registerFragmentThemeCallbacks() {
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(
                            @NonNull FragmentManager fragmentManager,
                            @NonNull Fragment fragment,
                            @NonNull android.view.View view,
                            @Nullable Bundle savedInstanceState
                    ) {
                        if (!(fragment instanceof DialFragment)) {
                            AppVisualThemeManager.applyRoot(MainActivity.this, view);
                        }
                    }
                },
                true
        );
    }

    /**
     * Keeps page content clear of status bars and display cut-outs while allowing the
     * bottom navigation surface to extend behind the system navigation area.
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

    private void setupBottomNavigation(@Nullable Bundle savedInstanceState) {
        binding.bottomNavigation.setOnItemSelectedListener(item ->
                openMainSection(item.getItemId())
        );

        int selectedItemId = R.id.nav_home;
        if (savedInstanceState != null) {
            int restored = savedInstanceState.getInt(
                    STATE_SELECTED_NAVIGATION_ITEM,
                    R.id.nav_home
            );
            if (isVisibleNavigationItem(restored)) {
                selectedItemId = restored;
            }
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

        if (pendingDialRequest) {
            openPendingDialPad();
        }
    }

    /**
     * Dashboard actions can continue using nav_dial internally even though Dial is no longer
     * a visible bottom-navigation item.
     */
    public void selectMainSection(@IdRes int itemId) {
        if (binding == null) {
            return;
        }
        if (itemId == R.id.nav_dial) {
            openDialPad();
            return;
        }
        if (isVisibleNavigationItem(itemId)) {
            binding.bottomNavigation.setSelectedItemId(itemId);
        }
    }

    public void openDialPad() {
        openDialPad(null);
    }

    private void openDialPad(@Nullable String phoneNumber) {
        Fragment fragment = phoneNumber == null || phoneNumber.trim().isEmpty()
                ? new DialFragment()
                : DialFragment.newInstance(phoneNumber.trim());

        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.mainFragmentContainer, fragment, "dial")
                .commit();
    }

    private void openPendingDialPad() {
        String number = pendingDialNumber;
        pendingDialRequest = false;
        pendingDialNumber = null;
        openDialPad(number);
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

    private boolean isVisibleNavigationItem(@IdRes int itemId) {
        return itemId == R.id.nav_home
                || itemId == R.id.nav_calls
                || itemId == R.id.nav_contacts
                || itemId == R.id.nav_protection;
    }

    private void applyVisualTheme() {
        if (binding == null) {
            return;
        }
        binding.main.setBackground(AppVisualThemeManager.createBackground(this));
        AppVisualThemeManager.applyMainNavigation(
                this,
                binding.bottomNavigationSurface,
                binding.bottomNavigation
        );

        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.mainFragmentContainer);
        if (current != null && !(current instanceof DialFragment) && current.getView() != null) {
            AppVisualThemeManager.applyRoot(this, current.getView());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String currentSignature = ThemePreferences.signature(this);
        if (!currentSignature.equals(appliedThemeSignature)) {
            appliedThemeSignature = currentSignature;
            recreate();
            return;
        }
        applyVisualTheme();
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (captureExternalDialIntent(intent) && binding != null) {
            openPendingDialPad();
        }
    }

    private boolean captureExternalDialIntent(@Nullable Intent intent) {
        if (intent == null || !Intent.ACTION_DIAL.equals(intent.getAction())) {
            return false;
        }

        pendingDialRequest = true;
        pendingDialNumber = extractPhoneNumber(intent.getData());
        return true;
    }

    @Nullable
    private String extractPhoneNumber(@Nullable Uri data) {
        if (data == null || !"tel".equalsIgnoreCase(data.getScheme())) {
            return null;
        }

        String number = data.getSchemeSpecificPart();
        if (number == null || number.trim().isEmpty()) {
            return null;
        }

        return number.trim();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        int selected = binding == null
                ? R.id.nav_home
                : binding.bottomNavigation.getSelectedItemId();
        if (!isVisibleNavigationItem(selected)) {
            selected = R.id.nav_home;
        }
        outState.putInt(STATE_SELECTED_NAVIGATION_ITEM, selected);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
