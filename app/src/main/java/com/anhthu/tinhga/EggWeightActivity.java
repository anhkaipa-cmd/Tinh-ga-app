package com.anhthu.tinhga;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.view.WindowManager;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class EggWeightActivity extends Activity {
    private static final int NAVY=Color.rgb(10,55,112);
    private static final int BLUE=Color.rgb(22,135,248);
    private static final int GREEN=Color.rgb(19,116,82);
    private static final int BORDER=Color.rgb(213,226,239);
    private static final int PALE=Color.rgb(236,246,255);
    private static final int BG=Color.rgb(248,251,255);
    private static final int GREY=Color.rgb(105,124,148);

    private SharedPreferences store;
    private final ArrayList<WeightRow>[] rows=new ArrayList[3];
    private final TextView[] dayStatusTexts=new TextView[3];
    private final LinearLayout[] dayContainers=new LinearLayout[3];
    private final TextView[] dayHeadingTexts=new TextView[3];
    private final View[] dayCards=new View[3];
    private TextView overallStatus;
    private final TextView[] machineCountViews=new TextView[3];
    private final int[] machineCounts=new int[3];
    private final TextView[] incubationInputs=new TextView[3];
    private final TextView[] hatchOutputs=new TextView[3];
    private Button exportButton, thirdDayToggle;
    private int visibleDayCount;
    private static final int REQUEST_SAVE_EXCEL = 6118;

    private static class WeightRow {
        final String id;
        EditText machine, house, age, top, middle, bottom;
        TextView average, productionDate;
        View container;
        WeightRow(String id) { this.id=id; }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        store=getSharedPreferences("tinh_ga_can_trung",MODE_PRIVATE);
        for (int i=0; i<3; i++) rows[i]=new ArrayList<>();
        visibleDayCount=3;
        // Suggested incubation dates: the next Thursday, Friday and Saturday.
        // Each date has its own saved value and may be changed independently.
        Calendar anchor=Calendar.getInstance();
        int delta=(Calendar.THURSDAY-anchor.get(Calendar.DAY_OF_WEEK)+7)%7;
        anchor.add(Calendar.DAY_OF_MONTH,delta);
        anchor.set(Calendar.HOUR_OF_DAY,12);
        anchor.set(Calendar.MINUTE,0);
        anchor.set(Calendar.SECOND,0);
        anchor.set(Calendar.MILLISECOND,0);
        SharedPreferences.Editor dateEditor=store.edit();
        for(int d=0;d<3;d++) {
            if(store.getLong("incubation_"+d,0)==0) {
                Calendar candidate=(Calendar)anchor.clone();
                candidate.add(Calendar.DAY_OF_MONTH,d);
                dateEditor.putLong("incubation_"+d,candidate.getTimeInMillis());
            }
        }
        dateEditor.apply();
        // Migrate the old shared count once, then keep each incubation date independent.
        int oldSharedCount=store.getInt("machine_count",1);
        oldSharedCount=Math.max(1,Math.min(10,oldSharedCount));
        SharedPreferences.Editor migrate=store.edit();
        for(int day=0;day<3;day++) {
            String key="machine_count_"+day;
            int previous=store.getInt(key,0);
            if(previous==0) {
                previous=oldSharedCount;
                migrate.putInt(key,previous);
            }
            machineCounts[day]=Math.max(1,Math.min(10,previous));
        }
        migrate.apply();
        setContentView(buildScreen());
        refresh();
    }

    private View buildScreen() {
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        // Respect status/navigation bar insets on Android 15+ edge-to-edge screens.
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(0,insets.getSystemWindowInsetTop(),
                0,insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(13),dp(14),dp(13),dp(24));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        TextView heading=label("Cân trứng",25,NAVY,true);
        root.addView(heading);
        TextView description=label("Theo ngày sản xuất và từng nhà • trọng lượng (g)",13,GREY,false);
        root.addView(description);
        root.addView(space(10));
        root.addView(navigation());
        root.addView(space(12));
        // Each incubation day has its own machine-count selector below.
        TextView guide=label("Mỗi đợt có ngày ấp và số máy riêng (tối đa 10). Mỗi máy có ngày SX, mã máy, mã nhà, tuần tuổi và 3 vị trí cân (5.000–18.000 g).",13,GREY,false);
        root.addView(guide);
        root.addView(space(7));

        for(int day=0;day<3;day++) {
            dayCards[day]=dayCard(day);
            root.addView(dayCards[day]);
            root.addView(space(9));
        }
        overallStatus=label("",13,NAVY,true);
        root.addView(overallStatus);
        root.addView(space(9));

        exportButton=new Button(this);
        exportButton.setAllCaps(false);
        exportButton.setText("GỬI EXCEL CÂN TRỨNG");
        exportButton.setTextColor(Color.WHITE);
        exportButton.setTextSize(16);
        exportButton.setTypeface(Typeface.DEFAULT_BOLD);
        exportButton.setOnClickListener(v->sendExcel());
        root.addView(exportButton,new LinearLayout.LayoutParams(-1,dp(54)));

        root.addView(space(9));
        Button newWeighing=new Button(this);
        newWeighing.setAllCaps(false);
        newWeighing.setText("NHẬP LẦN MỚI – GIỮ MÁY & ĐÀN");
        newWeighing.setTextSize(14);
        newWeighing.setTypeface(Typeface.DEFAULT_BOLD);
        newWeighing.setTextColor(NAVY);
        newWeighing.setBackground(round(PALE,11,BORDER));
        newWeighing.setOnClickListener(v->chooseClearWeights());
        root.addView(newWeighing,new LinearLayout.LayoutParams(-1,dp(50)));
        root.addView(label("Sau khi đã gửi/lưu Excel, chọn mục này để xóa Trên – Giữa – Dưới. Mã máy, mã nhà, tuần tuổi và ngày SX vẫn giữ để sửa lại.",12,GREY,false));
        return scroll;
    }

    private View navigation() {
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button doc=navButton("TÍNH DOC",false);
        Button weight=navButton("CÂN TRỨNG",true);
        doc.setOnClickListener(v->finish());
        weight.setOnClickListener(v->{});
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(42),1f);
        a.setMarginEnd(dp(6));
        bar.addView(doc,a);
        bar.addView(weight,new LinearLayout.LayoutParams(0,dp(42),1f));
        return bar;
    }

    private Button navButton(String title,boolean current) {
        Button b=new Button(this);
        b.setText(title); b.setAllCaps(false); b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(current?Color.WHITE:NAVY);
        b.setBackground(round(current?NAVY:PALE,13,current?NAVY:BORDER));
        b.setPadding(dp(5),0,dp(5),0);
        return b;
    }

    private void addIncubationRow(LinearLayout card,int day) {
        LinearLayout pair=new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout left=new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(label("Ngày ấp (chỉnh được)",12,GREY,false));
        TextView incub=dateButtonText();
        incubationInputs[day]=incub;
        incub.setTextSize(14);
        incub.setOnClickListener(v->pickDate("incubation_"+day,incub,false));
        left.addView(incub,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
        lp.setMarginEnd(dp(5));
        pair.addView(left,lp);

        LinearLayout right=new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(label("Ra gà (+21 ngày)",12,GREY,false));
        TextView hatch=dateButtonText();
        hatch.setTextSize(14);
        hatch.setTextColor(GREEN);
        hatchOutputs[day]=hatch;
        right.addView(hatch,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1f);
        rp.setMarginStart(dp(5));
        pair.addView(right,rp);
        card.addView(pair);
        card.addView(space(9));
    }

    private View machinePickerRow(int day) {
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labelBox=new LinearLayout(this);
        labelBox.setOrientation(LinearLayout.VERTICAL);
        labelBox.addView(label("Số máy cân của đợt này",14,NAVY,true));
        labelBox.addView(label("Tùy chỉnh riêng cho từng ngày ấp",11,GREY,false));
        bar.addView(labelBox,new LinearLayout.LayoutParams(0,-2,1f));
        TextView button=dateButtonText();
        button.setText(machineCounts[day]+" máy");
        machineCountViews[day]=button;
        button.setOnClickListener(v->pickMachineCount(day));
        bar.addView(button,new LinearLayout.LayoutParams(dp(103),dp(43)));
        return bar;
    }

    private void pickMachineCount(int day) {
        dismissKeyboard();
        NumberPicker picker=new NumberPicker(this);
        picker.setMinValue(1);
        picker.setMaxValue(10);
        picker.setWrapSelectorWheel(false);
        picker.setValue(machineCounts[day]);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        new AlertDialog.Builder(this)
            .setTitle("Chọn số máy cân – "+incubationHeading(store.getLong("incubation_"+day,System.currentTimeMillis())))
            .setView(picker)
            .setPositiveButton("ÁP DỤNG",(dialog,which)->{
                int count=picker.getValue();
                if(count!=machineCounts[day]) {
                    machineCounts[day]=count;
                    store.edit().putInt("machine_count_"+day,count).apply();
                    // Rebuild only after persisting; retain all existing input data.
                    for(int index=0;index<3;index++) rows[index].clear();
                    setContentView(buildScreen());
                    refresh();
                }
            })
            .setNegativeButton("HỦY",null)
            .show();
    }

    private View dayCard(int day) {
        LinearLayout card=verticalCard();
        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=label("ĐỢT ẤP",17,NAVY,true);
        dayHeadingTexts[day]=heading;
        card.addView(heading);
        card.addView(space(8));
        addIncubationRow(card,day);
        card.addView(machinePickerRow(day));
        card.addView(space(8));

        TextView status=label("",12,GREY,false);
        dayStatusTexts[day]=status;
        card.addView(status);
        card.addView(space(8));

        LinearLayout container=new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        dayContainers[day]=container;
        card.addView(container);

        String saved=store.getString("ids_"+day,"0");
        if(saved==null||saved.trim().isEmpty()) saved="0";
        ArrayList<String> ids=new ArrayList<>();
        for(String id:saved.split(",")) {
            if(!id.trim().isEmpty()) ids.add(id.trim());
        }
        while(ids.size()<machineCounts[day]) {
            long next=store.getLong("next_id",1);
            store.edit().putLong("next_id",next+1).apply();
            ids.add(String.valueOf(next));
        }
        store.edit().putString("ids_"+day,android.text.TextUtils.join(",",ids)).apply();
        for(int index=0;index<machineCounts[day];index++) appendRow(day,ids.get(index));
        long incub=store.getLong("incubation_"+day,0);
        heading.setText(incub>0?incubationHeading(incub):"CHỌN NGÀY ẤP");
        incubationInputs[day].setText(incub>0?formatDate(incub):"Chọn ngày ấp");
        hatchOutputs[day].setText(incub>0?formatDate(hatchMillis(incub)):"—");
        return card;
    }

    private void appendRow(int day,String id) {
        WeightRow row=new WeightRow(id);
        rows[day].add(row);
        LinearLayout card=new LinearLayout(this);
        row.container=card;
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9),dp(8),dp(9),dp(10));
        card.setBackground(round(Color.rgb(250,252,255),12,BORDER));
        LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);
        cardParams.bottomMargin=dp(6);
        dayContainers[day].addView(card,cardParams);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label("DÒNG CÂN "+rows[day].size(),13,NAVY,true),
            new LinearLayout.LayoutParams(0,-2,1f));
        Button remove=new Button(this);
        remove.setText("Xóa số");
        remove.setAllCaps(false);
        remove.setTextSize(12);
        remove.setTextColor(Color.rgb(190,77,58));
        remove.setPadding(dp(4),0,dp(4),0);
        remove.setBackground(round(Color.rgb(255,244,241),8,BORDER));
        remove.setOnClickListener(v->clearMachineRow(day,row));
        header.addView(remove,new LinearLayout.LayoutParams(dp(87),dp(34)));
        card.addView(header);

        LinearLayout rowOne=new LinearLayout(this);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);
        row.machine=input(day,id,"machine","Mã máy",false);
        row.house=input(day,id,"house","VD: V2",false);
        row.age=input(day,id,"age","Tuần tuổi",false);
        row.age.setInputType(InputType.TYPE_CLASS_NUMBER);
        addColumn(rowOne,"Máy cân",row.machine,1.15f);
        addColumn(rowOne,"Mã nhà",row.house,1.15f);
        addColumn(rowOne,"Tuần tuổi",row.age,1f);
        card.addView(rowOne);
        LinearLayout dateRow=new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.addView(label("Ngày sản xuất riêng của máy:",12,GREY,false),
            new LinearLayout.LayoutParams(0,-2,1f));
        row.productionDate=dateButtonText();
        row.productionDate.setTextSize(14);
        long production=store.getLong(machineDateKey(day,id),0);
        row.productionDate.setText(production>0?formatDate(production):"Chọn ngày SX");
        row.productionDate.setOnClickListener(v->pickMachineProduction(day,row));
        dateRow.addView(row.productionDate,new LinearLayout.LayoutParams(dp(158),dp(44)));
        LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(-1,-2);
        dateParams.setMargins(dp(2),dp(4),dp(2),dp(8));
        card.addView(dateRow,dateParams);

        LinearLayout weights=new LinearLayout(this);
        weights.setOrientation(LinearLayout.HORIZONTAL);
        row.top=input(day,id,"top","0",true);
        row.middle=input(day,id,"middle","0",true);
        row.bottom=input(day,id,"bottom","0",true);
        addColumn(weights,"Trên (g)",row.top,1f);
        addColumn(weights,"Giữa (g)",row.middle,1f);
        addColumn(weights,"Dưới (g)",row.bottom,1f);
        card.addView(weights);
        row.average=label("",13,GREEN,true);
        LinearLayout.LayoutParams av=new LinearLayout.LayoutParams(-1,-2);
        av.topMargin=dp(5);
        card.addView(row.average,av);
        updateAverage(row);
    }

    private void addColumn(LinearLayout into,String title,EditText field,float weight) {
        LinearLayout container=new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(label(title,12,GREY,false));
        container.addView(field,new LinearLayout.LayoutParams(-1,dp(43)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,weight);
        p.setMargins(dp(2),dp(3),dp(2),dp(4));
        into.addView(container,p);
    }

    private EditText input(int day,String id,String key,String hint,boolean decimal) {
        EditText v=new EditText(this);
        v.setSingleLine(true);
        v.setTextSize(15);
        v.setTextColor(NAVY);
        v.setPadding(dp(8),0,dp(8),0);
        v.setBackground(round(Color.WHITE,10,BORDER));
        v.setHint(hint);
        v.setHintTextColor(Color.rgb(170,185,203));
        if (decimal) v.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        else if ("age".equals(key)) v.setInputType(InputType.TYPE_CLASS_NUMBER);
        else v.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        String pref="entry_"+day+"_"+id+"_"+key;
        v.setText(store.getString(pref,""));
        v.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {}
            public void afterTextChanged(Editable s) {
                store.edit().putString(pref,s.toString()).apply();
                refresh();
            }
        });
        return v;
    }

    private void clearMachineRow(int day, WeightRow row) {
        store.edit().remove(machineDateKey(day,row.id)).apply();
        row.productionDate.setText("Chọn ngày SX");
        row.machine.setText("");
        row.house.setText("");
        row.age.setText("");
        row.top.setText("");
        row.middle.setText("");
        row.bottom.setText("");
        refresh();
    }

    /** Close Android IME before opening a date picker or export selector. */
    private void dismissKeyboard() {
        View focused=getCurrentFocus();
        try {
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null) {
                View target=focused!=null?focused:getWindow().getDecorView();
                imm.hideSoftInputFromWindow(target.getWindowToken(),0);
            }
        } catch(Exception ignored) {}
        if(focused!=null) {
            focused.clearFocus();
        }
        getWindow().setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN |
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void presentDatePicker(DatePickerDialog dialog) {
        dismissKeyboard();
        if(dialog.getWindow()!=null) {
            dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN |
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        // Wait for the previous EditText keyboard to finish collapsing.
        getWindow().getDecorView().postDelayed(()->{
            if(!isFinishing() && !isDestroyed()) {
                dialog.show();
                if(dialog.getWindow()!=null) {
                    dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN |
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                }
            }
        },200);
    }

    private String machineDateKey(int day,String id) {
        return "machine_production_"+day+"_"+id;
    }

    private void pickMachineProduction(int day, WeightRow row) {
        String key=machineDateKey(day,row.id);
        long saved=store.getLong(key,0);
        long incub=store.getLong("incubation_"+day,0);
        Calendar cal=Calendar.getInstance();
        if(saved>0) cal.setTimeInMillis(saved);
        else if(incub>0) {
            cal.setTimeInMillis(incub);
            cal.add(Calendar.DAY_OF_MONTH,-1);
        }
        DatePickerDialog dialog=new DatePickerDialog(this,(picker,year,month,date)->{
            Calendar chosen=Calendar.getInstance();
            chosen.clear();
            chosen.set(year,month,date,12,0,0);
            long when=chosen.getTimeInMillis();
            if(incub>0 && when>incub) {
                Toast.makeText(this,"Ngày sản xuất không được sau ngày ấp.",Toast.LENGTH_LONG).show();
                return;
            }
            store.edit().putLong(key,when).apply();
            row.productionDate.setText(formatDate(when));
            refresh();
        },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH));
        if(incub>0) dialog.getDatePicker().setMaxDate(incub);
        presentDatePicker(dialog);
    }

    private void pickDate(String key,TextView output,boolean production) {
        long prior=store.getLong(key,0);
        Calendar cal=Calendar.getInstance();
        if(prior>0) cal.setTimeInMillis(prior);
        DatePickerDialog dialog=new DatePickerDialog(this,(picker,year,month,date)->{
            Calendar chosen=Calendar.getInstance();
            chosen.clear();
            chosen.set(year,month,date,12,0,0);
            long when=chosen.getTimeInMillis();
            store.edit().putLong(key,when).apply();
            updateDates();
            refresh();
        },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH));
        presentDatePicker(dialog);
    }

    private String incubationHeading(long ms) {
        Calendar cal=Calendar.getInstance();
        cal.setTimeInMillis(ms);
        int dow=cal.get(Calendar.DAY_OF_WEEK);
        String name=dow==Calendar.THURSDAY?"THỨ 5":dow==Calendar.FRIDAY?"THỨ 6":
            dow==Calendar.SATURDAY?"THỨ 7":new SimpleDateFormat("EEEE",new Locale("vi","VN"))
                .format(new Date(ms)).toUpperCase(new Locale("vi","VN"));
        return name+" • "+formatDate(ms);
    }

    private void updateDates() {
        for(int i=0;i<3;i++) {
            long incub=store.getLong("incubation_"+i,0);
            if(incubationInputs[i]!=null)
                incubationInputs[i].setText(incub>0?formatDate(incub):"Chọn ngày ấp");
            if(hatchOutputs[i]!=null)
                hatchOutputs[i].setText(incub>0?formatDate(hatchMillis(incub)):"—");
            if(dayHeadingTexts[i]!=null)
                dayHeadingTexts[i].setText(incub>0?incubationHeading(incub):"CHỌN NGÀY ẤP");
        }
    }

    private long hatchMillis(long millis) {
        Calendar cal=Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.add(Calendar.DAY_OF_MONTH,21);
        return cal.getTimeInMillis();
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy",Locale.US).format(new Date(millis));
    }

    private void updateAverage(WeightRow r) {
        if (r.average==null) return;
        double top=weight(r.top), middle=weight(r.middle), bottom=weight(r.bottom);
        if(Double.isFinite(top) && Double.isFinite(middle) && Double.isFinite(bottom)) {
            double average=(top+middle+bottom)/3.0;
            r.average.setText("Trung bình: "+EggWeightValue.display(average)+" g");
        } else {
            r.average.setText("Trung bình: —");
        }
    }

    private double weight(EditText v) {
        return v==null ? Double.NaN : EggWeightValue.parse(v.getText().toString());
    }

    private boolean containsData(WeightRow r) {
        for(EditText v:new EditText[]{r.machine,r.house,r.age,r.top,r.middle,r.bottom}) {
            if(v!=null&&!v.getText().toString().trim().isEmpty()) return true;
        }
        return false;
    }

    private boolean complete(int day, WeightRow r) {
        if(r.machine==null||r.house==null||r.age==null) return false;
        long incubation=store.getLong("incubation_"+day,0);
        long production=store.getLong(machineDateKey(day,r.id),0);
        if(incubation<=0 || production<=0 || production>incubation) return false;
        if(r.machine.getText().toString().trim().isEmpty() || r.house.getText().toString().trim().isEmpty()) return false;
        int age;
        try { age=Integer.parseInt(r.age.getText().toString().trim()); }
        catch (Exception e) { return false; }
        return age>=1 && age<=120 &&
            !Double.isNaN(weight(r.top)) &&
            !Double.isNaN(weight(r.middle)) &&
            !Double.isNaN(weight(r.bottom));
    }

    // Each completed machine can be exported, even if other machines remain blank.
    private int completedMachineCount(int day) {
        int count=0;
        for(WeightRow row:rows[day]) if(complete(day,row)) count++;
        return count;
    }

    private void refresh() {
        if(overallStatus==null||exportButton==null) return;
        int total=0;
        int days=0;
        for(int day=0;day<3;day++) {
            for(WeightRow row:rows[day]) updateAverage(row);
            int count=completedMachineCount(day);
            total+=count;
            if(count>0) days++;
            if(dayStatusTexts[day]!=null) {
                dayStatusTexts[day].setText(count==machineCounts[day]?
                    "✓ Đã đủ "+count+" máy cân":
                    "Đã nhập "+count+"/"+machineCounts[day]+" máy • có thể xuất máy đã đủ");
                dayStatusTexts[day].setTextColor(count>0?GREEN:GREY);
            }
        }
        exportButton.setVisibility(View.VISIBLE);
        exportButton.setEnabled(true);
        exportButton.setBackground(round(BLUE,12,BORDER));
        overallStatus.setText(total>0?
            "✓ Có "+total+" máy cân đủ dữ liệu trong "+days+
                " đợt ấp. Có thể GỬI/LƯU Excel ngay; máy chưa đủ không xuất.":
            "Chưa có máy cân đủ dữ liệu. Nhập mã máy, nhà, tuần tuổi, ngày SX và 3 trọng lượng hợp lệ.");
        overallStatus.setTextColor(total>0?GREEN:NAVY);
    }

    /** Explicitly clear only the measured weights; never delete machine or flock identifiers. */
    private void chooseClearWeights() {
        dismissKeyboard();
        String[] labels=new String[3];
        boolean[] selected=new boolean[3];
        int lastMask=store.getInt("last_export_days_mask",0);
        for(int day=0;day<3;day++) {
            long incubation=store.getLong("incubation_"+day,0);
            labels[day]=(incubation>0?incubationHeading(incubation):"Đợt "+(day+1))+
                " • "+machineCounts[day]+" máy";
            selected[day]=(lastMask & (1<<day)) != 0;
        }
        new AlertDialog.Builder(this)
            .setTitle("Chọn ngày ấp để nhập cân lần mới")
            .setMultiChoiceItems(labels,selected,(dialog,index,checked)->selected[index]=checked)
            .setNegativeButton("HỦY",null)
            .setPositiveButton("TIẾP TỤC",(dialog,which)->{
                int mask=0;
                for(int day=0;day<3;day++) if(selected[day]) mask|=1<<day;
                if(mask==0) {
                    Toast.makeText(this,"Hãy chọn ít nhất một ngày ấp.",Toast.LENGTH_LONG).show();
                    return;
                }
                final int toClear=mask;
                new AlertDialog.Builder(this)
                    .setTitle("Xóa các số cân?")
                    .setMessage("Chỉ xóa trọng lượng TRÊN, GIỮA, DƯỚI của ngày đã chọn. " +
                        "Giữ nguyên mã máy, mã nhà/đàn, tuần tuổi, ngày sản xuất và số máy. " +
                        "Hãy chắc chắn đã gửi hoặc lưu Excel trước khi xóa.")
                    .setNegativeButton("GIỮ NGUYÊN",null)
                    .setPositiveButton("XÓA TRỌNG LƯỢNG",(confirmation,button)->clearWeights(toClear))
                    .show();
            })
            .show();
    }

    private void clearWeights(int dayMask) {
        SharedPreferences.Editor updates=store.edit();
        for(int day=0;day<3;day++) {
            if((dayMask&(1<<day))==0) continue;
            for(WeightRow row:rows[day]) {
                // The TextWatchers save the blank values immediately.
                row.top.setText("");
                row.middle.setText("");
                row.bottom.setText("");
                for(String key:new String[]{"top","middle","bottom"}) {
                    updates.remove("entry_"+day+"_"+row.id+"_"+key);
                }
                updateAverage(row);
            }
        }
        updates.remove("last_export_days_mask");
        updates.apply();
        refresh();
        Toast.makeText(this,
            "Đã xóa trọng lượng. Đã giữ máy, nhà/đàn, tuần tuổi và ngày sản xuất.",
            Toast.LENGTH_LONG).show();
    }

    private void sendExcel() {
        dismissKeyboard();
        boolean[] ready=completedDays();
        ArrayList<Integer> available=new ArrayList<>();
        ArrayList<String> labels=new ArrayList<>();
        for(int day=0;day<3;day++) {
            if(ready[day]) {
                available.add(day);
                long incub=store.getLong("incubation_"+day,0);
                labels.add(incubationHeading(incub)+" • "+machineCounts[day]+" máy cân");
            }
        }
        if(available.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Chưa có đợt ấp hoàn chỉnh")
                .setMessage("Mỗi máy cân cần đủ mã máy, mã nhà, tuần tuổi, ngày sản xuất và trọng lượng Trên – Giữa – Dưới từ 5.000–18.000 g. Hoàn thành ít nhất một đợt để xuất.")
                .setPositiveButton("ĐÃ HIỂU",null)
                .show();
            return;
        }
        boolean[] selected=new boolean[available.size()];
        java.util.Arrays.fill(selected,true);
        new AlertDialog.Builder(this)
            .setTitle("Chọn ngày ấp để xuất Excel")
            .setMultiChoiceItems(labels.toArray(new String[0]),selected,
                (dlg,idx,checked)->selected[idx]=checked)
            .setNegativeButton("HỦY",null)
            .setNeutralButton("LƯU EXCEL",(dlg,button)->{
                ArrayList<Integer> days=selectedDays(available,selected);
                if(days.isEmpty()) {
                    Toast.makeText(this,"Chọn ít nhất một ngày ấp.",Toast.LENGTH_LONG).show();
                    return;
                }
                buildExcel(days,true);
            })
            .setPositiveButton("GỬI EXCEL",(dlg,button)->{
                ArrayList<Integer> days=selectedDays(available,selected);
                if(days.isEmpty()) {
                    Toast.makeText(this,"Chọn ít nhất một ngày ấp.",Toast.LENGTH_LONG).show();
                    return;
                }
                buildExcel(days,false);
            })
            .show();
    }

    private ArrayList<Integer> selectedDays(ArrayList<Integer> available,boolean[] selected) {
        ArrayList<Integer> days=new ArrayList<>();
        for(int i=0;i<available.size();i++) if(selected[i]) days.add(available.get(i));
        return days;
    }

    private void buildExcel(List<Integer> selectedDays,boolean saveToDevice) {
        if(selectedDays==null || selectedDays.isEmpty()) return;
        String weeklyName=null;
        int selectedMask=0;
        for(int day:selectedDays) {
            selectedMask|=1<<day;
            long incubation=store.getLong("incubation_"+day,0);
            if(incubation<=0) return;
            String candidate=WlossWeekName.forIncubationDate(incubation);
            if(weeklyName!=null && !weeklyName.equals(candidate)) {
                new AlertDialog.Builder(this)
                    .setTitle("Các ngày ấp khác tuần")
                    .setMessage("Để đặt đúng tên WLOSS theo tuần, hãy chọn các ngày ấp thuộc cùng một tuần Thứ 2–Chủ nhật.")
                    .setPositiveButton("ĐÃ HIỂU",null)
                    .show();
                return;
            }
            weeklyName=candidate;
        }
        final String excelName=weeklyName;
        final int exportedMask=selectedMask;
        ArrayList<XlsxExporter.Record> data=new ArrayList<>();
        for(int day:selectedDays) {
            long incub=store.getLong("incubation_"+day,0);
            if(incub<=0 || rows[day].size()!=machineCounts[day]) {
                Toast.makeText(this,"Số máy cân chưa khớp với dữ liệu.",Toast.LENGTH_LONG).show();
                return;
            }
            for(WeightRow r:rows[day]) {
                if(!complete(day,r)) {
                    Toast.makeText(this,"Vẫn có máy cân thiếu ngày SX hoặc trọng lượng hợp lệ.",Toast.LENGTH_LONG).show();
                    return;
                }
                long machineProduction=store.getLong(machineDateKey(day,r.id),0);
                data.add(new XlsxExporter.Record(
                    formatDate(incub),formatDate(hatchMillis(incub)),
                    formatDate(machineProduction),
                    r.machine.getText().toString().trim(),
                    r.house.getText().toString().trim(),
                    Integer.parseInt(r.age.getText().toString().trim()),
                    weight(r.top),weight(r.middle),weight(r.bottom)));
            }
        }
        try {
            File dir=new File(getCacheDir(),"egg_exports");
            if(!dir.exists()&&!dir.mkdirs()) throw new IllegalStateException("Không tạo được thư mục.");
            File file=new File(dir,"Can-trung.xlsx");
            StringBuilder counts=new StringBuilder();
            for(int day:selectedDays) {
                if(counts.length()>0) counts.append(" | ");
                counts.append(incubationHeading(store.getLong("incubation_"+day,0)))
                      .append(": ").append(machineCounts[day]);
            }
            XlsxExporter.write(file,selectedDays.size(),counts.toString(),data);
            // Keep the internal temporary workbook name stable, but expose a weekly WLOSS
            // filename to the Android share provider and the device Save As dialog.
            store.edit().putString("last_excel_filename",excelName)
                .putInt("last_export_days_mask",exportedMask).apply();
            if(saveToDevice) {
                Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                save.addCategory(Intent.CATEGORY_OPENABLE);
                save.setType(EggExcelProvider.MIME);
                save.putExtra(Intent.EXTRA_TITLE,excelName);
                startActivityForResult(save,REQUEST_SAVE_EXCEL);
            } else {
                Intent send=new Intent(Intent.ACTION_SEND);
                send.setType(EggExcelProvider.MIME);
                send.putExtra(Intent.EXTRA_SUBJECT,excelName);
                send.putExtra(Intent.EXTRA_STREAM,EggExcelProvider.SHARE_URI);
                send.setClipData(ClipData.newRawUri(excelName,EggExcelProvider.SHARE_URI));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent chooser=Intent.createChooser(send,"Gửi Excel cân trứng");
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(chooser);
                Toast.makeText(this,
                    "Đã tạo "+excelName+". Sau khi gửi, có thể chọn NHẬP LẦN MỚI.",
                    Toast.LENGTH_LONG).show();
            }
        } catch(Exception e) {
            Toast.makeText(this,"Không xuất được Excel: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_SAVE_EXCEL || resultCode!=Activity.RESULT_OK
                || data==null || data.getData()==null) return;
        Uri destination=data.getData();
        File file=new File(new File(getCacheDir(),"egg_exports"),"Can-trung.xlsx");
        try(InputStream input=new FileInputStream(file);
            OutputStream output=getContentResolver().openOutputStream(destination)) {
            if(output==null) throw new IllegalStateException("Không mở được file đích.");
            byte[] buffer=new byte[8192];
            int n;
            while((n=input.read(buffer))!=-1) output.write(buffer,0,n);
            output.flush();
            String savedName=store.getString("last_excel_filename","wloss.xlsx");
            Toast.makeText(this,"Đã lưu file "+savedName,Toast.LENGTH_LONG).show();
            new AlertDialog.Builder(this)
                .setTitle("Đã lưu WLOSS")
                .setMessage("File "+savedName+" đã lưu. Bạn có muốn xóa trọng lượng để cân lần sau, nhưng giữ mã máy và mã nhà/đàn?")
                .setNegativeButton("GIỮ NGUYÊN",null)
                .setPositiveButton("CHỌN NGÀY ĐỂ XÓA",(dialog,button)->chooseClearWeights())
                .show();
        } catch(Exception e) {
            Toast.makeText(this,"Lưu Excel thất bại: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout verticalCard() {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12),dp(12),dp(12),dp(12));
        card.setBackground(round(Color.WHITE,16,BORDER));
        return card;
    }
    private TextView dateButtonText() {
        TextView v=label("Chọn ngày",15,NAVY,true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(round(PALE,10,BORDER));
        v.setPadding(dp(5),0,dp(5),0);
        return v;
    }
    private TextView label(String s,int size,int color,boolean bold) {
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        if(bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }
    private View space(int h) { View v=new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h))); return v; }
    private GradientDrawable round(int fill,int radius,int stroke) {
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(fill); bg.setCornerRadius(dp(radius));
        bg.setStroke(dp(1),stroke);
        return bg;
    }
    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }
}