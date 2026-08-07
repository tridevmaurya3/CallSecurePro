package com.tridev.callsecurepro.setup;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.ActivityCallerProtectionSetupBinding;

/**
 * Step-by-step setup for Call Secure Pro.
 *
 * Permissions and the Android caller-screening role are requested only after an explicit
 * user tap. Default Phone app status is shown here, but its role request intentionally
 * stays locked until the complete in-call UI is implemented.
 */
public class CallerProtectionSetupActivity extends AppCompatActivity {

    private ActivityCallerProtectionSetupBinding binding;

    private final ActivityResultLauncher<String> contactsPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> refreshSetupStatus()
            );

    private final ActivityResultLauncher<String> callPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> refreshSetupStatus()
            );

    private final ActivityResultLauncher<Intent> callerRoleLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> refreshSetupStatus()
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        binding = ActivityCallerProtectionSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applySystemInsets();
        setupActions();
        refreshSetupStatus();
    }

    private void applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.setupRoot, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );

            view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
            );
            return windowInsets;
        });

        ViewCompat.requestApplyInsets(binding.setupRoot);
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.finishSetupButton.setOnClickListener(view -> finish());

        binding.contactsPermissionButton.setOnClickListener(view -> {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
            }
        });

        binding.callPermissionButton.setOnClickListener(view -> {
            if (hasTelephonyFeature() && !hasPermission(Manifest.permission.CALL_PHONE)) {
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE);
            }
        });

        binding.callerRoleButton.setOnClickListener(view -> requestCallerScreeningRole());

        // Intentionally locked until the full user-facing in-call screen is complete.
        binding.defaultPhoneRoleButton.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) {
            refreshSetupStatus();
        }
    }

    private void refreshSetupStatus() {
        int completedItems = 0;

        boolean callerRoleReady = updateCallerRoleStatus();
        if (callerRoleReady) {
            completedItems++;
        }

        boolean contactsGranted = hasPermission(Manifest.permission.READ_CONTACTS);
        updatePermissionStatus(
                binding.contactsStatusChip,
                binding.contactsPermissionButton,
                contactsGranted,
                R.string.setup_contacts_action
        );
        if (contactsGranted) {
            completedItems++;
        }

        boolean callPermissionReady;
        if (!hasTelephonyFeature()) {
            setUnavailableStatus(
                    binding.callStatusChip,
                    binding.callPermissionButton,
                    R.string.setup_status_no_telephony
            );
            callPermissionReady = true;
        } else {
            boolean callGranted = hasPermission(Manifest.permission.CALL_PHONE);
            updatePermissionStatus(
                    binding.callStatusChip,
                    binding.callPermissionButton,
                    callGranted,
                    R.string.setup_call_action
            );
            callPermissionReady = callGranted;
        }

        if (callPermissionReady) {
            completedItems++;
        }

        updateDefaultPhoneIntegrationStatus();

        binding.setupProgressText.setText(
                getString(R.string.setup_progress_format, completedItems, 3)
        );
    }

    private boolean updateCallerRoleStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            setUnavailableStatus(
                    binding.callerRoleStatusChip,
                    binding.callerRoleButton,
                    R.string.setup_status_android_10_required
            );
            return true;
        }

        RoleManager roleManager = getSystemService(RoleManager.class);
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            setUnavailableStatus(
                    binding.callerRoleStatusChip,
                    binding.callerRoleButton,
                    R.string.setup_status_not_available
            );
            return true;
        }

        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            setGrantedStatus(
                    binding.callerRoleStatusChip,
                    binding.callerRoleButton,
                    R.string.setup_status_active
            );
            return true;
        }

        setRequiredStatus(
                binding.callerRoleStatusChip,
                binding.callerRoleButton,
                R.string.setup_screening_action
        );
        return false;
    }

    private void requestCallerScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            refreshSetupStatus();
            return;
        }

        RoleManager roleManager = getSystemService(RoleManager.class);
        if (roleManager == null
                || !roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                || roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            refreshSetupStatus();
            return;
        }

        callerRoleLauncher.launch(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        );
    }

    private void updateDefaultPhoneIntegrationStatus() {
        if (!hasTelephonyFeature()) {
            setUnavailableStatus(
                    binding.defaultPhoneStatusChip,
                    binding.defaultPhoneRoleButton,
                    R.string.setup_default_phone_no_telephony
            );
            return;
        }

        if (isDefaultPhoneApp()) {
            setGrantedStatus(
                    binding.defaultPhoneStatusChip,
                    binding.defaultPhoneRoleButton,
                    R.string.setup_default_phone_active
            );
            return;
        }

        setPreparedStatus(
                binding.defaultPhoneStatusChip,
                binding.defaultPhoneRoleButton
        );
    }

    private boolean isDefaultPhoneApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            return roleManager != null
                    && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
                    && roleManager.isRoleHeld(RoleManager.ROLE_DIALER);
        }

        TelecomManager telecomManager =
                (TelecomManager) getSystemService(TELECOM_SERVICE);
        String defaultDialerPackage = telecomManager == null
                ? null
                : telecomManager.getDefaultDialerPackage();
        return getPackageName().equals(defaultDialerPackage);
    }

    private void updatePermissionStatus(
            @NonNull Chip statusChip,
            @NonNull MaterialButton actionButton,
            boolean granted,
            int requestButtonTextRes
    ) {
        if (granted) {
            setGrantedStatus(
                    statusChip,
                    actionButton,
                    R.string.setup_status_allowed
            );
        } else {
            setRequiredStatus(statusChip, actionButton, requestButtonTextRes);
        }
    }

    private void setGrantedStatus(
            @NonNull Chip statusChip,
            @NonNull MaterialButton actionButton,
            int statusTextRes
    ) {
        int foreground = ContextCompat.getColor(this, R.color.csp_safe);
        int background = ContextCompat.getColor(this, R.color.csp_safe_container);

        statusChip.setText(statusTextRes);
        statusChip.setTextColor(foreground);
        statusChip.setChipBackgroundColor(ColorStateList.valueOf(background));

        actionButton.setText(R.string.setup_action_done);
        actionButton.setEnabled(false);
    }

    private void setRequiredStatus(
            @NonNull Chip statusChip,
            @NonNull MaterialButton actionButton,
            int actionTextRes
    ) {
        int foreground = ContextCompat.getColor(this, R.color.csp_unknown);
        int background = ContextCompat.getColor(this, R.color.csp_unknown_container);

        statusChip.setText(R.string.setup_status_required);
        statusChip.setTextColor(foreground);
        statusChip.setChipBackgroundColor(ColorStateList.valueOf(background));

        actionButton.setText(actionTextRes);
        actionButton.setEnabled(true);
    }

    private void setPreparedStatus(
            @NonNull Chip statusChip,
            @NonNull MaterialButton actionButton
    ) {
        int foreground = ContextCompat.getColor(this, R.color.csp_primary);
        int background = ContextCompat.getColor(this, R.color.csp_primary_container);

        statusChip.setText(R.string.setup_default_phone_ready);
        statusChip.setTextColor(foreground);
        statusChip.setChipBackgroundColor(ColorStateList.valueOf(background));

        actionButton.setText(R.string.setup_default_phone_action_locked);
        actionButton.setEnabled(false);
    }

    private void setUnavailableStatus(
            @NonNull Chip statusChip,
            @NonNull MaterialButton actionButton,
            int statusTextRes
    ) {
        int foreground = ContextCompat.getColor(this, R.color.csp_text_muted);
        int background = ContextCompat.getColor(this, R.color.csp_surface_variant);

        statusChip.setText(statusTextRes);
        statusChip.setTextColor(foreground);
        statusChip.setChipBackgroundColor(ColorStateList.valueOf(background));

        actionButton.setText(R.string.setup_action_not_needed);
        actionButton.setEnabled(false);
    }

    private boolean hasPermission(@NonNull String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasTelephonyFeature() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
