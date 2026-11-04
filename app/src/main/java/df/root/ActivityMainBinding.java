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
import android.widget.Toolbar;

/**
 * Hand-written stand-in for the viewbinding class AGP used to generate. Every
 * binding.<id> call site in MainActivity keeps working and only the import and the
 * inflate line changed, but the app no longer depends on
 * androidx.databinding:viewbinding.
 *
 * Each field is resolved once here, at inflate time, which is exactly what the
 * generated code did. A missing id throws at inflation rather than showing up as a
 * compile error, so the list below has to stay in step with activity_main.xml.
 */
final class ActivityMainBinding {

    private final RelativeLayout root;

    final LinearLayout         toolbar;
    final TextView             odinLabel;
    final ImageButton          menuAnchor;
    final View                 dimOverlay;
    final CardStackLayout      cardStack;
    final DraggableCardView    cardFetchKsu;
    final LinearLayout         rowFetchKsu;
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
        cardFetchKsu         = inflated.findViewById(R.id.cardFetchKsu);
        rowFetchKsu          = inflated.findViewById(R.id.rowFetchKsu);
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
        btnKsu               = inflated.findViewById(R.id.btnKsu);
        ksuChevron           = inflated.findViewById(R.id.ksuChevron);
        ksuLabel             = inflated.findViewById(R.id.ksuLabel);
        odinPill             = inflated.findViewById(R.id.odinPill);
        odinDot              = inflated.findViewById(R.id.odinDot);
    }

    static ActivityMainBinding inflate(LayoutInflater inflater) {
        return new ActivityMainBinding(inflater.inflate(R.layout.activity_main, null));
    }

    RelativeLayout getRoot() {
        return root;
    }
}
