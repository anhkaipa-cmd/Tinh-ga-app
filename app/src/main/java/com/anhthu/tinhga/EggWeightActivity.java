package com.anhthu.tinhga;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.Intent;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
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
    private final TextView[] dayDateTexts=new TextView[3];
    private final TextView[] dayStatusTexts=new TextView[3];
    private final LinearLayout[] dayContainers=new LinearLayout[3];
    private TextView hatchText, incubationText, overallStatus;
    private Button exportButton;

    private static class WeightRow {
        final String id;
        EditText house, age, top, middle, bottom;
        TextView average;
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
        setContentView(buildScreen());
        refresh();
    }

    private View buildScreen() {
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
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
        root.addView(incubationCard());
        root.addView(space(8));

        TextView guide=label("Nhập 3 ngày sản xuất khác nhau. Mỗi nhà cân 3 vị trí: Trên, Giữa, Dưới.",13,GREY,false);
        root.addView(guide);
        root.addView(space(7));

        for(int day=0;day<3;day++) {
            root.addView(dayCard(day));
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

    private View incubationCard() {
        LinearLayout card=verticalCard();
        card.addView(label("Ngày ấp và ngày ra gà",16,NAVY,true));
        card.addView(space(9));
        LinearLayout pair=new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout left=new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(label("Ngày ấp",12,GREY,false));
        incubationText=dateButtonText();
        incubationText.setOnClickListener(v->pickDate("incubation",incubationText,false));
        left.addView(incubationText, new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,-2,1f);
        p1.setMarginEnd(dp(5));
        pair.addView(left,p1);

        LinearLayout right=new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(label("Ngày ra gà (+21 ngày)",12,GREY,false));
        hatchText=dateButtonText();
        hatchText.setBackground(round(PALE,10,BORDER));
        hatchText.setTextColor(GREEN);
        right.addView(hatchText,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,-2,1f);
        p2.setMarginStart(dp(5));
        pair.addView(right,p2);
        card.addView(pair);
        updateDates();
        return card;
    }

    private View dayCard(int day) {
        LinearLayout card=verticalCard();
        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(label("NGÀY " +(day+1),17,NAVY,true),
            new LinearLayout.LayoutParams(0,-2,1f));
        TextView date=dateButtonText();
        date.setTextSize(14);
        dayDateTexts[day]=date;
        date.setOnClickListener(v->pickDate("production_"+day,date,true));
        titleRow.addView(date,new LinearLayout.LayoutParams(dp(158),dp(43)));
        card.addView(titleRow);

        TextView status=label("",12,GREY,false);
        dayStatusTexts[day]=status;
        card.addView(status);
        card.addView(space(8));

        LinearLayout container=new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        dayContainers[day]=container;
        card.addView(container);

        String saved=store.getString("ids_"+day,"0");
        if (saved.trim().isEmpty()) saved="0";
        for(String id:saved.split(",")) {
            if (!id.trim().isEmpty()) appendRow(day,id.trim());
        }
        Button add=new Button(this);
        add.setAllCaps(false);
        add.setText("+ THÊM NHÀ");
        add.setTextSize(13);
        add.setTextColor(NAVY);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setBackground(round(PALE,10,BORDER));
        add.setOnClickListener(v->addHouse(day));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(43));
        ap.topMargin=dp(7);
        card.addView(add,ap);

        long ms=store.getLong("production_"+day,0);
        date.setText(ms>0?formatDate(ms):"Chọn ngày SX");
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
        header.addView(label("NHÀ "+rows[day].size(),13,NAVY,true),
            new LinearLayout.LayoutParams(0,-2,1f));
        Button remove=new Button(this);
        remove.setText("Xóa nhà");
        remove.setAllCaps(false);
        remove.setTextSize(12);
        remove.setTextColor(Color.rgb(190,77,58));
        remove.setPadding(dp(4),0,dp(4),0);
        remove.setBackground(round(Color.rgb(255,244,241),8,BORDER));
        remove.setOnClickListener(v->removeHouse(day,row));
        header.addView(remove,new LinearLayout.LayoutParams(dp(87),dp(34)));
        card.addView(header);

        LinearLayout rowOne=new LinearLayout(this);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);
        row.house=input(day,id,"house","Mã nhà (VD: V2)",false);
        row.age=input(day,id,"age","Tuần tuổi",false);
        row.age.setInputType(InputType.TYPE_CLASS_NUMBER);
        addColumn(rowOne,"Tên nhà",row.house,1.7f);
        addColumn(rowOne,"Tuần tuổi",row.age,1f);
        card.addView(rowOne);

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

    private void addHouse(int day) {
        long id=store.getLong("next_id",1);
        store.edit().putLong("next_id",id+1).apply();
        appendRow(day,String.valueOf(id));
        saveIds(day);
        refresh();
    }

    private void removeHouse(int day,WeightRow row) {
        if (rows[day].size()==1) {
            row.house.setText("");row.age.setText("");
            row.top.setText("");row.middle.setText("");row.bottom.setText("");
        } else {
            dayContainers[day].removeView(row.container);
            rows[day].remove(row);
            SharedPreferences.Editor ed=store.edit();
            for(String field:new String[]{"house","age","top","middle","bottom"}) {
                ed.remove("entry_"+day+"_"+row.id+"_"+field);
            }
            ed.apply();
            saveIds(day);
        }
        refresh();
    }

    private void saveIds(int day) {
        StringBuilder ids=new StringBuilder();
        for(WeightRow r:rows[day]) {
            if(ids.length()>0) ids.append(',');
            ids.append(r.id);
        }
        store.edit().putString("ids_"+day,ids.toString()).apply();
    }

    private void pickDate(String key,TextView output,boolean production) {
        long prior=store.getLong(key,0);
        Calendar cal=Calendar.getInstance();
        if(prior>0) cal.setTimeInMillis(prior);
        DatePickerDialog dialog=new DatePickerDialog(this,(picker,year,month,date)->{
            Calendar chosen=Calendar.getInstance();
            chosen.clear();
            chosen.set(year,month,date);
            store.edit().putLong(key,chosen.getTimeInMillis()).apply();
            output.setText(formatDate(chosen.getTimeInMillis()));
            updateDates();
            refresh();
        },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH));
        if(production) {
            long incub=store.getLong("incubation",0);
            if(incub>0) dialog.getDatePicker().setMaxDate(incub);
        }
        dialog.show();
    }

    private void updateDates() {
        long incub=store.getLong("incubation",0);
        if(incubationText!=null) incubationText.setText(incub>0?formatDate(incub):"Chọn ngày ấp");
        if(hatchText!=null) hatchText.setText(incub>0?formatDate(hatchMillis(incub)):"Tự động +21");
        for(int i=0;i<3;i++) {
            if(dayDateTexts[i]!=null) {
                long date=store.getLong("production_"+i,0);
                dayDateTexts[i].setText(date>0?formatDate(date):"Chọn ngày SX");
            }
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
        if(!Double.isNaN(top)&&!Double.isNaN(middle)&&!Double.isNaN(bottom)) {
            r.average.setText("Trung bình: "+String.format(Locale.US,"%.2f", (top+middle+bottom)/3)+" g");
        } else r.average.setText("Trung bình: —");
    }

    private double weight(EditText v) {
        if (v==null) return Double.NaN;
        try {
            String text=v.getText().toString().trim().replace(',','.');
            if(text.isEmpty()) return Double.NaN;
            double x=Double.parseDouble(text);
            return (x>0 && x<=500 && !Double.isInfinite(x))?x:Double.NaN;
        } catch(Exception e) { return Double.NaN; }
    }

    private boolean containsData(WeightRow r) {
        for(EditText v:new EditText[]{r.house,r.age,r.top,r.middle,r.bottom}) {
            if(v!=null&&!v.getText().toString().trim().isEmpty()) return true;
        }
        return false;
    }

    private boolean complete(WeightRow r) {
        if(r.house==null||r.age==null) return false;
        if(r.house.getText().toString().trim().isEmpty()) return false;
        int age;
        try { age=Integer.parseInt(r.age.getText().toString().trim()); }
        catch (Exception e) { return false; }
        return age>=1 && age<=120 &&
            !Double.isNaN(weight(r.top)) &&
            !Double.isNaN(weight(r.middle)) &&
            !Double.isNaN(weight(r.bottom));
    }

    private void refresh() {
        if(overallStatus==null||exportButton==null) return;
        int validDays=0;
        boolean incomplete=false;
        HashSet<Long> distinct=new HashSet<>();
        for(int day=0;day<3;day++) {
            long date=store.getLong("production_"+day,0);
            if(date>0) distinct.add(date);
            int filled=0;
            boolean partial=false;
            for(WeightRow r:rows[day]) {
                updateAverage(r);
                if(complete(r)) filled++;
                else if(containsData(r)) partial=true;
            }
            boolean dayReady=date>0 && filled>0 && !partial;
            if(dayReady) validDays++;
            if(partial) incomplete=true;
            if(dayStatusTexts[day]!=null) {
                String status=dayReady?"✓ Đủ dữ liệu • "+filled+" nhà":
                    (partial?"Còn dòng chưa đủ 3 vị trí hoặc tuần tuổi":
                    (date==0?"Chọn ngày sản xuất":"Nhập ít nhất 1 nhà và đủ 3 vị trí"));
                dayStatusTexts[day].setText(status);
                dayStatusTexts[day].setTextColor(dayReady?GREEN:GREY);
            }
        }
        boolean validDate=store.getLong("incubation",0)>0;
        boolean unique=distinct.size()==3;
        boolean allReady=validDate && validDays==3 && unique && !incomplete;
        exportButton.setEnabled(allReady);
        exportButton.setVisibility(allReady?View.VISIBLE:View.GONE);
        exportButton.setBackground(round(allReady?BLUE:Color.rgb(137,155,181),12,BORDER));
        if(!validDate) overallStatus.setText("Cần chọn ngày ấp để xuất Excel.");
        else if(!unique && validDays==3) overallStatus.setText("3 ngày sản xuất phải khác nhau.");
        else if(allReady) overallStatus.setText("✓ Đã đủ 3 ngày. Có thể gửi file Excel.");
        else overallStatus.setText("Đã hoàn thành "+validDays+"/3 ngày • Hoàn thiện để bật nút gửi Excel.");
        overallStatus.setTextColor(allReady?GREEN:NAVY);
    }

    private void sendExcel() {
        long incub=store.getLong("incubation",0);
        if(incub<=0) return;
        HashSet<Long> distinct=new HashSet<>();
        ArrayList<XlsxExporter.Record> data=new ArrayList<>();
        for(int day=0;day<3;day++) {
            long prod=store.getLong("production_"+day,0);
            if(prod<=0||!distinct.add(prod)) {
                Toast.makeText(this,"Cần đủ 3 ngày sản xuất khác nhau.",Toast.LENGTH_LONG).show();
                return;
            }
            int num=0;
            for(WeightRow r:rows[day]) {
                if(complete(r)) {
                    data.add(new XlsxExporter.Record(formatDate(prod),
                        r.house.getText().toString().trim(),
                        Integer.parseInt(r.age.getText().toString().trim()),
                        weight(r.top),weight(r.middle),weight(r.bottom)));
                    num++;
                } else if (containsData(r)) {
                    Toast.makeText(this,"Có nhà chưa nhập đủ dữ liệu.",Toast.LENGTH_LONG).show();
                    return;
                }
            }
            if(num==0) return;
        }
        try {
            File dir=new File(getCacheDir(),"egg_exports");
            if(!dir.exists()&&!dir.mkdirs()) throw new IllegalStateException("Không tạo được thư mục xuất.");
            File xlsx=new File(dir,"Can-trung.xlsx");
            XlsxExporter.write(xlsx,formatDate(incub),formatDate(hatchMillis(incub)),data);
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            send.putExtra(Intent.EXTRA_SUBJECT,"Báo cáo cân trứng");
            send.putExtra(Intent.EXTRA_STREAM,EggExcelProvider.SHARE_URI);
            send.setClipData(ClipData.newRawUri("Can-trung.xlsx",EggExcelProvider.SHARE_URI));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send,"Gửi Excel cân trứng"));
        } catch(Exception e) {
            Toast.makeText(this,"Lỗi xuất Excel: "+e.getMessage(),Toast.LENGTH_LONG).show();
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