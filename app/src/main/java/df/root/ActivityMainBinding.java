package df.root;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class ActivityMainBinding {

    private final RelativeLayout root;

    final LinearLayout         toolbar;
    final TextView             odinLabel;
    final ImageButton          menuAnchor;
    final View                 dimOverlay;
    final CardStackLayout      cardStack;
    final DraggableCardView    cardSuManager;
    final LinearLayout         rowSuManager;
    final TextView             suManagerSubtitle;
    final DraggableCardView    cardBoot;
    final TextView             tvAutorunTitle;
    final TextView             tvAutorunDesc;
    final OneUiSwitch          switchBootStart;
    final TextView             tvRebootTitle;
    final TextView             tvRebootDesc;
    final OneUiSwitch          switchAutoSoftReboot;
    final DraggableCardView    cardModules;
    final TextView             modulesSubtitle;
    final OneUiSwitch          switchModules;
    final DraggableCardView    cardLog;
    final OneUiSwitch          switchAdvancedLog;
    final ScrollView           logWindow;
    final TextView             outputView;
    final RelativeLayout       bottomBar;
    final TwoStepProgressView  twoStep;
    final Button               btnRun;
    final ImageButton          btnShareLog;
    final FrameLayout          btnKsu;
    final ImageView            ksuChevron;
    final TextView             ksuLabel;
    final LinearLayout         odinPill;
    final View                 odinDot;

    private ActivityMainBinding(View inflated) {
        this.root = (RelativeLayout) inflated;
        toolbar              = inflated.findViewById(R.id.toolbar);
        odinLabel            = inflated.findViewById(R.id.odinLabel);
        menuAnchor           = inflated.findViewById(R.id.menuAnchor);
        dimOverlay           = inflated.findViewById(R.id.dimOverlay);
        cardStack            = inflated.findViewById(R.id.cardStack);
        cardSuManager        = inflated.findViewById(R.id.cardSuManager);
        rowSuManager         = inflated.findViewById(R.id.rowSuManager);
        suManagerSubtitle    = inflated.findViewById(R.id.suManagerSubtitle);
        cardBoot             = inflated.findViewById(R.id.cardBoot);
        tvAutorunTitle       = inflated.findViewById(R.id.tvAutorunTitle);
        tvAutorunDesc        = inflated.findViewById(R.id.tvAutorunDesc);
        switchBootStart      = inflated.findViewById(R.id.switchBootStart);
        tvRebootTitle        = inflated.findViewById(R.id.tvRebootTitle);
        tvRebootDesc         = inflated.findViewById(R.id.tvRebootDesc);
        switchAutoSoftReboot = inflated.findViewById(R.id.switchAutoSoftReboot);
        cardModules          = inflated.findViewById(R.id.cardModules);
        modulesSubtitle      = inflated.findViewById(R.id.modulesSubtitle);
        switchModules        = inflated.findViewById(R.id.switchModules);
        cardLog              = inflated.findViewById(R.id.cardLog);
        switchAdvancedLog    = inflated.findViewById(R.id.switchAdvancedLog);
        logWindow            = inflated.findViewById(R.id.logWindow);
        outputView           = inflated.findViewById(R.id.outputView);
        bottomBar            = inflated.findViewById(R.id.bottomBar);
        twoStep              = inflated.findViewById(R.id.twoStep);
        btnRun               = inflated.findViewById(R.id.btnRun);
        btnShareLog          = inflated.findViewById(R.id.btnShareLog);
        
        // Заглушки для опциональных элементов из исходников, если они не задействованы напрямую в этой разметке
        btnKsu               = null;
        ksuChevron           = null;
        ksuLabel             = null;
        odinPill             = null;
        odinDot              = null;
    }

    static ActivityMainBinding inflate(LayoutInflater inflater) {
        return new ActivityMainBinding(inflater.inflate(R.layout.activity_main, null));
    }

    RelativeLayout getRoot() {
        return root;
    }
}

