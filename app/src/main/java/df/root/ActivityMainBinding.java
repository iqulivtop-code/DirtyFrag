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

final class ActivityMainBinding {

    private final RelativeLayout root;

    final LinearLayout         toolbar;
    final TextView             odinLabel;
    final ImageButton          menuAnchor;
    final ImageButton          btnMenu;
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
    final ScrollView           outputScroll;
    final TextView             outputView;
    final RelativeLayout       bottomBar;
    final TwoStepProgressView  twoStep;
    final CustomButton         btnRun;
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
        btnMenu              = menuAnchor;
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
        outputScroll         = logWindow;
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

    // Чистый класс-наследник android.widget.Button со всеми необходимыми заглушками методов
    static class CustomButton extends android.widget.Button {
        public CustomButton(android.content.Context context) { super(context); }
        public CustomButton(android.content.Context context, android.util.AttributeSet attrs) { super(context, attrs); }
        public CustomButton(android.content.Context context, android.util.AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }
        
        public void setStrokeColor(android.content.res.ColorStateList color) {
            // Заглушка для совместимости
        }
    }
}
