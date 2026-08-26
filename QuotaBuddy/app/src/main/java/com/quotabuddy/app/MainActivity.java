package com.quotabuddy.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int EXPORT_BACKUP = 40;
    private static final int IMPORT_BACKUP = 41;

    private static final int BG = Color.rgb(9,9,11);
    private static final int SURFACE = Color.rgb(20,20,24);
    private static final int SURFACE_2 = Color.rgb(29,29,34);
    private static final int TEXT = Color.rgb(246,246,247);
    private static final int MUTED = Color.rgb(159,159,170);
    private static final int ACCENT = Color.rgb(200,255,46);
    private static final int DANGER = Color.rgb(255,91,97);
    private static final int WARNING = Color.rgb(255,184,77);

    private Db db;
    private Ledger ledger;
    private LinearLayout root;
    private LinearLayout content;
    private String currentTab = "home";
    private LocalDate selectedDate;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Db(this);
        ledger = new Ledger(db);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.getDecorView().getWindowInsetsController().setSystemBarsAppearance(0,
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        }
        showHome();
    }

    private int dp(int x){ return Math.round(x * getResources().getDisplayMetrics().density); }

    private GradientDrawable shape(int color, float radiusDp){
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp((int)radiusDp)); return g;
    }
    private GradientDrawable outlined(int color, float radiusDp, int strokeColor){
        GradientDrawable g=shape(color,radiusDp);g.setStroke(dp(1),strokeColor);return g;
    }

    private TextView tv(String text, float sp, int color, boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(sp);t.setTextColor(color);t.setIncludeFontPadding(false);
        if(bold)t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));else t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        return t;
    }

    private TextView titleText(String s){return tv(s,29,TEXT,true);}
    private TextView body(String s){TextView t=tv(s,15,TEXT,false);t.setLineSpacing(0,1.15f);return t;}
    private TextView muted(String s){TextView t=tv(s,13,MUTED,false);t.setLineSpacing(0,1.1f);return t;}
    private TextView money(String s){return tv(s,29,TEXT,true);}

    private LinearLayout vbox(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout hbox(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}

    private void margin(View v,int l,int t,int r,int b){ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)v.getLayoutParams();p.setMargins(dp(l),dp(t),dp(r),dp(b));v.setLayoutParams(p);}
    private void addGap(ViewGroup g,int h){View v=new View(this);g.addView(v,new ViewGroup.LayoutParams(1,dp(h)));}

    private LinearLayout card(){
        LinearLayout l=vbox();l.setPadding(dp(17),dp(16),dp(17),dp(16));l.setBackground(shape(SURFACE,22));
        l.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return l;
    }

    private TextView pill(String text,int fg,int bg){
        TextView t=tv(text.toUpperCase(Locale.ROOT),11,fg,true);t.setLetterSpacing(.07f);t.setPadding(dp(10),dp(6),dp(10),dp(6));t.setBackground(shape(bg,99));return t;
    }

    private Button button(String label, boolean primary){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(primary?Color.BLACK:TEXT);b.setBackground(primary?shape(ACCENT,15):outlined(SURFACE_2,15,Color.rgb(55,55,62)));
        b.setMinHeight(dp(48));b.setPadding(dp(16),0,dp(16),0);return b;
    }

    private TextView actionIcon(String glyph){
        TextView t=tv(glyph,23,TEXT,false);t.setGravity(Gravity.CENTER);t.setBackground(shape(SURFACE_2,16));t.setMinWidth(dp(48));t.setMinHeight(dp(48));t.setPadding(dp(12),dp(8),dp(12),dp(8));return t;
    }

    private void screen(String title,String subtitle,boolean showBack,Runnable onBack,String actionGlyph,Runnable action,boolean nav){
        root=vbox();root.setBackgroundColor(BG);root.setPadding(dp(18),dp(12),dp(18),0);
        LinearLayout bar=hbox();
        if(showBack){TextView back=actionIcon("‹");back.setOnClickListener(v->onBack.run());bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        LinearLayout headings=vbox();headings.setPadding(showBack?dp(12):0,dp(2),dp(8),0);headings.addView(titleText(title));if(subtitle!=null&&!subtitle.isEmpty()){addGap(headings,4);headings.addView(muted(subtitle));}
        bar.addView(headings,new LinearLayout.LayoutParams(0,-2,1));
        if(actionGlyph!=null){TextView a=actionIcon(actionGlyph);a.setOnClickListener(v->action.run());bar.addView(a,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        addGap(root,18);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);content=vbox();content.setPadding(0,0,0,dp(28));sv.addView(content,new ScrollView.LayoutParams(-1,-2));root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        if(nav)root.addView(bottomNav(),new LinearLayout.LayoutParams(-1,dp(76)));
        setContentView(root);
    }

    private View bottomNav(){
        LinearLayout nav=hbox();nav.setPadding(dp(4),dp(9),dp(4),dp(9));nav.setBackground(outlined(SURFACE,23,Color.rgb(40,40,46)));
        nav.addView(navItem("⌂","Home","home",this::showHome),new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navItem("◫","Family","families",this::showFamilies),new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navItem("○","Persone","people",this::showPeople),new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navItem("↕","Pagamenti","payments",this::showPayments),new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navItem("⚙","Altro","settings",this::showSettings),new LinearLayout.LayoutParams(0,-1,1));
        return nav;
    }

    private View navItem(String icon,String label,String key,Runnable click){
        LinearLayout box=vbox();box.setGravity(Gravity.CENTER);boolean on=key.equals(currentTab);TextView i=tv(icon,20,on?ACCENT:MUTED,false);TextView l=tv(label,10,on?TEXT:MUTED,on);l.setGravity(Gravity.CENTER);box.addView(i);addGap(box,3);box.addView(l);box.setOnClickListener(v->click.run());return box;
    }

    private void showHome(){
        currentTab="home";screen("QuotaBuddy","Shared subscriptions, senza fogli Excel",false,null,"+",this::familyDialog,true);
        List<Models.Subscription> subs=db.subscriptions(false);LocalDate today=LocalDate.now();
        long received=db.receivedBetween(today.withDayOfMonth(1),today.withDayOfMonth(today.lengthOfMonth()));long due=0,credit=0;int late=0;
        for(Models.Subscription s:subs){Models.SubscriptionSummary z=ledger.summary(s,today);due+=z.outstandingCents;credit+=z.advanceCents;late+=z.lateMembers;}
        LinearLayout hero=card();hero.setBackground(shape(Color.rgb(17,17,20),24));hero.addView(muted("RICEVUTO QUESTO MESE"));addGap(hero,7);hero.addView(money(eur(received)));
        addGap(hero,17);LinearLayout metrics=hbox();metrics.addView(metric("Da ricevere",eur(due),due>0?DANGER:TEXT),new LinearLayout.LayoutParams(0,-2,1));metrics.addView(metric("In ritardo",String.valueOf(late),late>0?WARNING:TEXT),new LinearLayout.LayoutParams(0,-2,1));metrics.addView(metric("Anticipi",eur(credit),ACCENT),new LinearLayout.LayoutParams(0,-2,1));hero.addView(metrics);content.addView(hero);
        addGap(content,24);sectionTitle("Le tue Family",subs.isEmpty()?null:"Vedi tutte",subs.isEmpty()?null:this::showFamilies);
        if(subs.isEmpty()){
            LinearLayout empty=card();empty.setGravity(Gravity.CENTER_HORIZONTAL);TextView q=tv("Q",44,ACCENT,true);empty.addView(q);addGap(empty,12);TextView t=tv("Crea la prima Family",20,TEXT,true);empty.addView(t);addGap(empty,7);TextView d=muted("Spotify, Netflix o qualsiasi abbonamento condiviso. QuotaBuddy calcola quote, arretrati e anticipi per te.");d.setGravity(Gravity.CENTER);empty.addView(d);addGap(empty,18);Button b=button("Crea Family",true);b.setOnClickListener(v->familyDialog());empty.addView(b,new LinearLayout.LayoutParams(-1,dp(50)));content.addView(empty);
        }else{
            for(Models.Subscription s:subs){addGap(content,10);content.addView(subscriptionCard(s));}
        }
        if(due>0){addGap(content,24);sectionTitle("Da sistemare",null,null);for(Models.Subscription s:subs){for(Models.MemberSnapshot ms:ledger.snapshots(s.id,today)){if(ms.state==Models.MemberState.LATE){addGap(content,9);content.addView(memberMiniCard(ms));}}}}
        ArrayList<Models.MemberSnapshot> upcoming=new ArrayList<>();for(Models.Subscription s:subs){for(Models.MemberSnapshot ms:ledger.snapshots(s.id,today)){if(ms.nextChargeDate!=null&&!ms.nextChargeDate.isBefore(today)&&ms.state!=Models.MemberState.ENDED)upcoming.add(ms);}}upcoming.sort(Comparator.comparing(x->x.nextChargeDate));
        if(!upcoming.isEmpty()){addGap(content,24);sectionTitle("Prossime scadenze",null,null);for(int i=0;i<Math.min(5,upcoming.size());i++){addGap(content,9);content.addView(upcomingMiniCard(upcoming.get(i)));}}
    }

    private LinearLayout metric(String label,String value,int valueColor){LinearLayout b=vbox();b.addView(muted(label));addGap(b,5);b.addView(tv(value,17,valueColor,true));return b;}

    private void sectionTitle(String left,String right,Runnable action){LinearLayout h=hbox();h.addView(tv(left,18,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));if(right!=null){TextView r=tv(right,13,ACCENT,true);r.setOnClickListener(v->action.run());h.addView(r);}content.addView(h);addGap(content,8);}

    private View subscriptionCard(Models.Subscription s){
        Models.SubscriptionSummary z=ledger.summary(s,LocalDate.now());LinearLayout c=card();c.setOnClickListener(v->showSubscription(s.id));
        LinearLayout top=hbox();TextView monogram=tv(s.name.substring(0,1).toUpperCase(Locale.ROOT),20,Color.BLACK,true);monogram.setGravity(Gravity.CENTER);monogram.setBackground(shape(ACCENT,15));top.addView(monogram,new LinearLayout.LayoutParams(dp(46),dp(46)));LinearLayout tx=vbox();tx.setPadding(dp(12),0,0,0);tx.addView(tv(s.name,18,TEXT,true));addGap(tx,4);tx.addView(muted(z.activeMembers+" membri · "+eur(z.currentPriceCents)+" / mese"));top.addView(tx,new LinearLayout.LayoutParams(0,-2,1));TextView p=z.lateMembers>0?pill(z.lateMembers+" late",DANGER,Color.rgb(53,24,28)):pill("ok",ACCENT,Color.rgb(34,48,22));top.addView(p);c.addView(top);addGap(c,16);
        LinearLayout bot=hbox();bot.addView(metric("Quota",eur(z.memberShareCents),TEXT),new LinearLayout.LayoutParams(0,-2,1));bot.addView(metric("Da ricevere",eur(z.outstandingCents),z.outstandingCents>0?DANGER:TEXT),new LinearLayout.LayoutParams(0,-2,1));bot.addView(metric("Rinnovo",shortDate(z.nextRenewal),TEXT),new LinearLayout.LayoutParams(0,-2,1));c.addView(bot);return c;
    }

    private View memberMiniCard(Models.MemberSnapshot ms){LinearLayout c=card();c.setOnClickListener(v->showSubscription(ms.subscription.id));LinearLayout h=hbox();TextView av=avatar(ms.person.name);h.addView(av,new LinearLayout.LayoutParams(dp(42),dp(42)));LinearLayout tx=vbox();tx.setPadding(dp(12),0,0,0);tx.addView(tv(ms.person.name,16,TEXT,true));addGap(tx,4);tx.addView(muted(ms.subscription.name+" · scadenza "+shortDate(ms.nextChargeDate)));h.addView(tx,new LinearLayout.LayoutParams(0,-2,1));h.addView(tv(eur(ms.outstandingCents),16,DANGER,true));c.addView(h);return c;}
    private View upcomingMiniCard(Models.MemberSnapshot ms){LinearLayout c=card();c.setOnClickListener(v->showSubscription(ms.subscription.id));LinearLayout h=hbox();h.addView(avatar(ms.person.name),new LinearLayout.LayoutParams(dp(42),dp(42)));LinearLayout tx=vbox();tx.setPadding(dp(12),0,0,0);tx.addView(tv(ms.person.name,16,TEXT,true));addGap(tx,4);String extra=ms.nextChargeAlreadyCoveredCents>0?" · già coperti "+eur(ms.nextChargeAlreadyCoveredCents):"";tx.addView(muted(ms.subscription.name+" · "+shortDate(ms.nextChargeDate)+extra));h.addView(tx,new LinearLayout.LayoutParams(0,-2,1));h.addView(tv(eur(ms.nextChargeRemainingCents),16,TEXT,true));c.addView(h);return c;}

    private TextView avatar(String name){String[] bits=name.trim().split("\\s+");String ini=bits.length==1?bits[0].substring(0,1):bits[0].substring(0,1)+bits[bits.length-1].substring(0,1);TextView a=tv(ini.toUpperCase(Locale.ROOT),13,TEXT,true);a.setGravity(Gravity.CENTER);a.setBackground(shape(SURFACE_2,99));return a;}

    private void showFamilies(){
        currentTab="families";screen("Family","Ogni abbonamento ha regole e storico separati",false,null,"+",this::familyDialog,true);List<Models.Subscription> list=db.subscriptions(true);
        if(list.isEmpty()){content.addView(emptyText("Nessuna Family ancora.","Crea Spotify, Netflix o il prossimo abbonamento che dividerai."));return;}
        for(Models.Subscription s:list){if(s.archived){TextView arch=muted("ARCHIVIATA");content.addView(arch);addGap(content,6);}content.addView(subscriptionCard(s));addGap(content,10);}
    }

    private void showSubscription(long subId){
        Models.Subscription s=db.subscription(subId);if(s==null){showFamilies();return;}screen(s.name,"Rinnovo ogni "+s.renewalDay+" del mese",true,this::showFamilies,"⋮",()->familyActions(s),false);
        Models.SubscriptionSummary z=ledger.summary(s,LocalDate.now());
        LinearLayout hero=card();LinearLayout top=hbox();LinearLayout a=vbox();a.addView(muted("COSTO ATTUALE"));addGap(a,5);a.addView(money(eur(z.currentPriceCents)));top.addView(a,new LinearLayout.LayoutParams(0,-2,1));TextView status=z.lateMembers>0?pill(z.lateMembers+" in ritardo",DANGER,Color.rgb(54,24,28)):pill("tutto ok",ACCENT,Color.rgb(34,48,22));top.addView(status);hero.addView(top);addGap(hero,16);LinearLayout m=hbox();m.addView(metric("Membri",String.valueOf(z.activeMembers),TEXT),new LinearLayout.LayoutParams(0,-2,1));m.addView(metric("Quota attuale",eur(z.memberShareCents),TEXT),new LinearLayout.LayoutParams(0,-2,1));m.addView(metric("Prossimo rinnovo",shortDate(z.nextRenewal),TEXT),new LinearLayout.LayoutParams(0,-2,1));hero.addView(m);content.addView(hero);
        addGap(content,22);sectionTitle("Membri",null,null);List<Models.MemberSnapshot> snapshots=ledger.snapshots(s.id,LocalDate.now());if(snapshots.isEmpty()){LinearLayout e=card();e.addView(body("Nessun membro. Aggiungi le persone che partecipano a questa Family."));addGap(e,13);Button b=button("Aggiungi membro",true);b.setOnClickListener(v->addMemberDialog(s));e.addView(b);content.addView(e);}else{for(Models.MemberSnapshot ms:snapshots){content.addView(memberCard(ms));addGap(content,9);}Button add=button("+ Aggiungi membro",false);add.setOnClickListener(v->addMemberDialog(s));content.addView(add);}
        addGap(content,24);sectionTitle("Storico costo",null,null);List<Models.PricePoint> prices=db.prices(s.id);for(int i=prices.size()-1;i>=0;i--){Models.PricePoint p=prices.get(i);LinearLayout row=card();LinearLayout h=hbox();LinearLayout x=vbox();x.addView(tv(eur(p.amountCents)+" / mese",16,TEXT,true));addGap(x,3);x.addView(muted("Dal ciclo del "+longDate(p.validFrom)));h.addView(x,new LinearLayout.LayoutParams(0,-2,1));if(i>0){TextView del=tv("×",24,MUTED,false);int idx=i;del.setOnClickListener(v->confirm("Eliminare questo cambio prezzo?","I calcoli dei mesi successivi verranno ricalcolati.",()->{db.deletePrice(prices.get(idx).id);showSubscription(s.id);}));h.addView(del);}row.addView(h);content.addView(row);addGap(content,8);}Button price=button("+ Aggiungi cambio prezzo",false);price.setOnClickListener(v->priceDialog(s));content.addView(price);
        addGap(content,24);LinearLayout split=card();split.addView(tv("Come viene diviso",17,TEXT,true));addGap(split,7);split.addView(body(s.includeOwner?"Il costo viene diviso tra tutti i membri attivi + te. La tua parte assorbe gli eventuali centesimi di arrotondamento.":"Il costo viene diviso soltanto tra i membri attivi della Family."));content.addView(split);
    }

    private View memberCard(Models.MemberSnapshot ms){
        LinearLayout c=card();c.setOnClickListener(v->membershipActions(ms));LinearLayout h=hbox();h.addView(avatar(ms.person.name),new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout tx=vbox();tx.setPadding(dp(12),0,0,0);tx.addView(tv(ms.person.name,17,TEXT,true));addGap(tx,4);String sub=statusSubline(ms);tx.addView(muted(sub));h.addView(tx,new LinearLayout.LayoutParams(0,-2,1));h.addView(statusPill(ms));c.addView(h);addGap(c,15);LinearLayout b=hbox();b.addView(metric("Versato",eur(ms.totalPaidCents),TEXT),new LinearLayout.LayoutParams(0,-2,1));if(ms.state==Models.MemberState.LATE)b.addView(metric("Manca",eur(ms.outstandingCents),DANGER),new LinearLayout.LayoutParams(0,-2,1));else b.addView(metric("Anticipo",eur(ms.advanceCents),ms.advanceCents>0?ACCENT:TEXT),new LinearLayout.LayoutParams(0,-2,1));b.addView(metric("Coperto fino",ms.coveredThrough==null?"—":monthYear(ms.coveredThrough),TEXT),new LinearLayout.LayoutParams(0,-2,1));c.addView(b);return c;
    }

    private String statusSubline(Models.MemberSnapshot ms){if(ms.state==Models.MemberState.LATE)return "Scaduto · "+eur(ms.outstandingCents)+" da recuperare";if(ms.state==Models.MemberState.CREDIT){if(ms.nextChargeDate!=null&&ms.nextChargeAlreadyCoveredCents>0&&ms.nextChargeRemainingCents>0)return eur(ms.nextChargeAlreadyCoveredCents)+" già coperti per "+monthYear(ms.nextChargeDate);return "Pagato in anticipo";}if(ms.state==Models.MemberState.UPCOMING)return "Entra dal "+shortDate(ms.membership.joinedOn);if(ms.state==Models.MemberState.ENDED)return "Partecipazione conclusa";return ms.nextChargeDate==null?"Tutto saldato":"Prossimo: "+shortDate(ms.nextChargeDate)+" · "+eur(ms.nextChargeRemainingCents);}
    private TextView statusPill(Models.MemberSnapshot ms){switch(ms.state){case LATE:return pill("late",DANGER,Color.rgb(54,24,28));case CREDIT:return pill("credit",ACCENT,Color.rgb(34,48,22));case UPCOMING:return pill("soon",WARNING,Color.rgb(55,41,21));case ENDED:return pill("ended",MUTED,Color.rgb(42,42,48));default:return pill("paid",ACCENT,Color.rgb(34,48,22));}}

    private void membershipActions(Models.MemberSnapshot ms){
        String[] opts={"Registra pagamento","Modifica partecipazione","Apri persona"};new AlertDialog.Builder(this).setTitle(ms.person.name+" · "+ms.subscription.name).setItems(opts,(d,w)->{if(w==0)paymentDialog(ms.subscription.id,ms.person.id,null,()->showSubscription(ms.subscription.id));else if(w==1)membershipDialog(ms.membership);else showPerson(ms.person.id);}).show();
    }

    private void familyActions(Models.Subscription s){String arch=s.archived?"Riattiva Family":"Archivia Family";String[] opts={"Modifica Family","Aggiungi membro","Aggiungi cambio prezzo",arch,"Elimina Family"};new AlertDialog.Builder(this).setTitle(s.name).setItems(opts,(d,w)->{if(w==0)familyEditDialog(s);else if(w==1)addMemberDialog(s);else if(w==2)priceDialog(s);else if(w==3){db.archiveSubscription(s.id,!s.archived);showSubscription(s.id);}else confirm("Eliminare "+s.name+"?","Verranno eliminati anche partecipazioni e pagamenti legati a questa Family.",()->{db.deleteSubscription(s.id);showFamilies();});}).show();}

    private void showPeople(){
        currentTab="people";screen("Persone","Una sola anagrafica, anche su più Family",false,null,"+",()->personDialog(null),true);List<Models.Person> people=db.people();if(people.isEmpty()){content.addView(emptyText("Nessuna persona.","Aggiungi un amico e poi collegalo a una o più Family."));return;}for(Models.Person p:people){LinearLayout c=card();c.setOnClickListener(v->showPerson(p.id));LinearLayout h=hbox();h.addView(avatar(p.name),new LinearLayout.LayoutParams(dp(46),dp(46)));LinearLayout tx=vbox();tx.setPadding(dp(12),0,0,0);tx.addView(tv(p.name,17,TEXT,true));addGap(tx,4);List<Models.Membership> ms=db.membershipsForPerson(p.id);tx.addView(muted(ms.size()+" Family"+(p.method==null||p.method.isEmpty()?"":" · "+p.method)));h.addView(tx,new LinearLayout.LayoutParams(0,-2,1));h.addView(tv("›",24,MUTED,false));c.addView(h);content.addView(c);addGap(content,9);}}

    private void showPerson(long personId){Models.Person p=db.person(personId);if(p==null){showPeople();return;}screen(p.name,p.method==null||p.method.isEmpty()?"Profilo persona":p.method,true,this::showPeople,"⋮",()->personActions(p),false);List<Models.Membership> ms=db.membershipsForPerson(p.id);long total=0;for(Models.Payment pay:db.payments())if(pay.personId==p.id)total+=pay.amountCents;LinearLayout hero=card();hero.addView(muted("TOTALE VERSATO"));addGap(hero,6);hero.addView(money(eur(total)));if(p.note!=null&&!p.note.trim().isEmpty()){addGap(hero,12);hero.addView(body(p.note));}content.addView(hero);addGap(content,22);sectionTitle("Family",null,null);if(ms.isEmpty())content.addView(emptyText("Nessuna Family collegata.","Puoi aggiungere questa persona dalla schermata di una Family."));for(Models.Membership m:ms){Models.MemberSnapshot snap=ledger.snapshot(m,LocalDate.now());content.addView(memberCard(snap));addGap(content,9);}addGap(content,22);sectionTitle("Pagamenti",null,null);for(Models.Payment pay:db.payments()){if(pay.personId!=p.id)continue;content.addView(paymentCard(pay));addGap(content,8);}}
    private void personActions(Models.Person p){String[] opts={"Modifica persona","Elimina persona"};new AlertDialog.Builder(this).setTitle(p.name).setItems(opts,(d,w)->{if(w==0)personDialog(p);else confirm("Eliminare "+p.name+"?","Verranno eliminati anche le sue partecipazioni e i pagamenti registrati.",()->{db.deletePerson(p.id);showPeople();});}).show();}

    private void showPayments(){currentTab="payments";screen("Pagamenti","Ogni movimento è modificabile o eliminabile",false,null,"+",()->paymentDialog(null,null,null),true);List<Models.Payment> ps=db.payments();if(ps.isEmpty()){content.addView(emptyText("Nessun pagamento registrato.","Quando qualcuno ti paga, aggiungi qui il movimento: QuotaBuddy lo spalmerà automaticamente sulle quote più vecchie."));return;}String last="";for(Models.Payment p:ps){String month=monthYear(p.paidAt);if(!month.equals(last)){content.addView(muted(month.toUpperCase(Locale.ITALY)));addGap(content,7);last=month;}content.addView(paymentCard(p));addGap(content,8);}}
    private View paymentCard(Models.Payment p){LinearLayout c=card();c.setOnClickListener(v->paymentDialog(p.subscriptionId,p.personId,p));LinearLayout h=hbox();TextView av=avatar(p.personName);h.addView(av,new LinearLayout.LayoutParams(dp(42),dp(42)));LinearLayout x=vbox();x.setPadding(dp(12),0,0,0);x.addView(tv(p.personName,16,TEXT,true));addGap(x,3);String info=p.subscriptionName+" · "+shortDate(p.paidAt)+(p.method==null||p.method.isEmpty()?"":" · "+p.method);x.addView(muted(info));h.addView(x,new LinearLayout.LayoutParams(0,-2,1));h.addView(tv("+ "+eur(p.amountCents),16,ACCENT,true));c.addView(h);if(p.note!=null&&!p.note.trim().isEmpty()){addGap(c,10);c.addView(muted(p.note));}return c;}

    private void showSettings(){currentTab="settings";screen("Altro","Dati locali, backup e informazioni",false,null,null,null,true);sectionTitle("Backup",null,null);LinearLayout b=card();b.addView(tv("I tuoi dati restano sul telefono",17,TEXT,true));addGap(b,6);b.addView(muted("Esporta un file completo di persone, Family, storico prezzi, partecipazioni e pagamenti. Il ripristino accetta anche i vecchi backup testuali di QuotaBuddy 1."));addGap(b,16);Button ex=button("Esporta backup",true);ex.setOnClickListener(v->exportBackup());b.addView(ex);addGap(b,9);Button im=button("Ripristina backup",false);im.setOnClickListener(v->chooseBackup());b.addView(im);content.addView(b);addGap(content,20);sectionTitle("QuotaBuddy",null,null);LinearLayout about=card();about.addView(tv("QuotaBuddy 2",18,TEXT,true));addGap(about,6);about.addView(muted("Offline-first · nessun account · nessun cloud obbligatorio"));addGap(about,13);about.addView(body("I pagamenti coprono sempre la quota più vecchia ancora scoperta. Cambi di prezzo e membri entrano in vigore dai cicli di rinnovo selezionati, senza riscrivere lo storico."));content.addView(about);}

    private View emptyText(String title,String desc){LinearLayout e=card();e.addView(tv(title,18,TEXT,true));addGap(e,6);e.addView(muted(desc));return e;}

    private void familyDialog(){
        LinearLayout l=form();EditText name=input("Nome Family (es. Spotify)");EditText price=input("Costo totale mensile (es. 20,99)");CheckBox owner=check("Dividi anche la tua quota",true);LocalDate def=LocalDate.now();selectedDate=def;Button date=dateButton("Primo ciclo",selectedDate);date.setOnClickListener(v->pickDate(selectedDate,d->{selectedDate=d;date.setText("Primo ciclo · "+longDate(d));}));l.addView(name);l.addView(price);l.addView(date);l.addView(owner);
        new AlertDialog.Builder(this).setTitle("Nuova Family").setView(l).setPositiveButton("Crea",(d,w)->{try{String n=name.getText().toString().trim();long cents=parseMoney(price.getText().toString());if(n.isEmpty()||cents<=0)throw new Exception();db.addSubscription(n,cents,selectedDate,owner.isChecked());showFamilies();}catch(Exception e){toast("Controlla nome, costo e data");}}).setNegativeButton("Annulla",null).show();
    }

    private void familyEditDialog(Models.Subscription s){LinearLayout l=form();EditText name=input("Nome");name.setText(s.name);l.addView(name);l.addView(muted(s.includeOwner?"Divisione storica: membri attivi + te":"Divisione storica: solo membri attivi"));new AlertDialog.Builder(this).setTitle("Modifica Family").setMessage("La regola di divisione resta fissa per non alterare le quote già maturate.").setView(l).setPositiveButton("Salva",(d,w)->{if(name.getText().toString().trim().isEmpty()){toast("Inserisci un nome");return;}db.updateSubscription(s.id,name.getText().toString(),s.includeOwner);showSubscription(s.id);}).setNegativeButton("Annulla",null).show();}

    private void priceDialog(Models.Subscription s){LinearLayout l=form();EditText amount=input("Nuovo costo mensile");selectedDate=LocalDate.now();Button date=dateButton("Valido dal",selectedDate);date.setOnClickListener(v->pickDate(selectedDate,d->{selectedDate=d;LocalDate cycle=ledger.cycleOnOrAfter(s,d);date.setText("Valido dal ciclo · "+longDate(cycle));}));l.addView(amount);l.addView(date);new AlertDialog.Builder(this).setTitle("Cambio prezzo").setMessage("Se scegli una data tra due rinnovi, il nuovo costo parte dal rinnovo successivo.").setView(l).setPositiveButton("Aggiungi",(d,w)->{try{long cents=parseMoney(amount.getText().toString());if(cents<=0)throw new Exception();LocalDate cycle=ledger.cycleOnOrAfter(s,selectedDate);db.addPrice(s.id,cents,cycle);showSubscription(s.id);}catch(Exception e){toast("Importo non valido");}}).setNegativeButton("Annulla",null).show();}

    private void addMemberDialog(Models.Subscription s){List<Models.Person> people=db.people();if(people.isEmpty()){new AlertDialog.Builder(this).setTitle("Prima serve una persona").setMessage("Crea l'anagrafica, poi la aggiungiamo alla Family.").setPositiveButton("Crea persona",(d,w)->personDialog(null)).setNegativeButton("Annulla",null).show();return;}LinearLayout l=form();Spinner sp=spinner();ArrayList<String> labels=new ArrayList<>();for(Models.Person p:people)labels.add(p.name);sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));selectedDate=s.startDate;Button date=dateButton("Membro dal",selectedDate);date.setOnClickListener(v->pickDate(selectedDate,d->{selectedDate=d;date.setText("Membro dal · "+longDate(ledger.cycleOnOrAfter(s,d)));}));l.addView(sp);l.addView(date);new AlertDialog.Builder(this).setTitle("Aggiungi membro").setView(l).setPositiveButton("Aggiungi",(d,w)->{Models.Person p=people.get(sp.getSelectedItemPosition());for(Models.Membership m:db.membershipsForSubscription(s.id))if(m.personId==p.id){toast("Questa persona è già nella Family");return;}LocalDate joined=ledger.cycleOnOrAfter(s,selectedDate);db.addMembership(s.id,p.id,joined,"");showSubscription(s.id);}).setNegativeButton("Annulla",null).show();}

    private void membershipDialog(Models.Membership m){Models.Subscription s=db.subscription(m.subscriptionId);Models.Person p=db.person(m.personId);if(s==null||p==null)return;LinearLayout l=form();LocalDate[] joined={m.joinedOn};LocalDate[] left={m.leftOn};Button j=dateButton("Membro dal",joined[0]);j.setOnClickListener(v->pickDate(joined[0],d->{joined[0]=ledger.cycleOnOrAfter(s,d);j.setText("Membro dal · "+longDate(joined[0]));}));Button end=dateButton("Uscita",left[0]);end.setText(left[0]==null?"Nessuna data di uscita":"Esce dal ciclo · "+longDate(left[0]));end.setOnClickListener(v->{String[] opts={"Imposta data di uscita","Resta attivo senza fine"};new AlertDialog.Builder(this).setItems(opts,(dd,which)->{if(which==1){left[0]=null;end.setText("Nessuna data di uscita");}else pickDate(left[0]==null?LocalDate.now():left[0],d->{left[0]=ledger.cycleOnOrAfter(s,d);end.setText("Esce dal ciclo · "+longDate(left[0]));});}).show();});l.addView(j);l.addView(end);new AlertDialog.Builder(this).setTitle(p.name+" · partecipazione").setView(l).setPositiveButton("Salva",(d,w)->{if(left[0]!=null&&!left[0].isAfter(joined[0])){toast("L'uscita deve essere successiva all'ingresso");return;}db.updateMembership(m.id,joined[0],left[0],m.note);showSubscription(s.id);}).setNeutralButton("Rimuovi",(d,w)->confirm("Rimuovere il membro?","Usalo solo per correggere un inserimento sbagliato. Per una vera uscita è meglio impostare una data di fine.",()->{db.deleteMembership(m.id);showSubscription(s.id);})).setNegativeButton("Annulla",null).show();}

    private void personDialog(Models.Person existing){LinearLayout l=form();EditText name=input("Nome");EditText method=input("Metodo abituale (Revolut, bonifico…)");EditText note=input("Note");if(existing!=null){name.setText(existing.name);method.setText(existing.method);note.setText(existing.note);}l.addView(name);l.addView(method);l.addView(note);new AlertDialog.Builder(this).setTitle(existing==null?"Nuova persona":"Modifica persona").setView(l).setPositiveButton("Salva",(d,w)->{String n=name.getText().toString().trim();if(n.isEmpty()){toast("Inserisci un nome");return;}if(existing==null){Models.Person dup=db.findPersonByName(n);if(dup!=null){toast("Esiste già una persona con questo nome");return;}db.addPerson(n,method.getText().toString().trim(),note.getText().toString().trim());showPeople();}else{db.updatePerson(existing.id,n,method.getText().toString().trim(),note.getText().toString().trim());showPerson(existing.id);}}).setNegativeButton("Annulla",null).show();}

    private static final class PairChoice{Models.Subscription s;Models.Person p;String label;@Override public String toString(){return label;}}

    private void paymentDialog(Long preSub,Long prePerson,Models.Payment existing){paymentDialog(preSub,prePerson,existing,this::showPayments);}

    private void paymentDialog(Long preSub,Long prePerson,Models.Payment existing,Runnable afterSave){
        List<PairChoice> choices=new ArrayList<>();for(Models.Subscription s:db.subscriptions(true)){for(Models.Membership m:db.membershipsForSubscription(s.id)){Models.Person p=db.person(m.personId);if(p==null)continue;PairChoice c=new PairChoice();c.s=s;c.p=p;c.label=p.name+" · "+s.name;choices.add(c);}}
        if(choices.isEmpty()){new AlertDialog.Builder(this).setTitle("Nessun membro disponibile").setMessage("Crea una Family e aggiungi almeno una persona prima di registrare un pagamento.").setPositiveButton("OK",null).show();return;}
        LinearLayout l=form();Spinner pair=spinner();ArrayAdapter<PairChoice> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices);pair.setAdapter(adapter);int selected=0;for(int i=0;i<choices.size();i++){if(preSub!=null&&prePerson!=null&&choices.get(i).s.id==preSub&&choices.get(i).p.id==prePerson){selected=i;break;}}pair.setSelection(selected);
        EditText amount=input("Importo");EditText method=input("Metodo");EditText note=input("Nota facoltativa");LocalDate[] date={existing==null?LocalDate.now():existing.paidAt};Button dateBtn=dateButton("Data",date[0]);dateBtn.setOnClickListener(v->pickDate(date[0],d->{date[0]=d;dateBtn.setText("Data · "+longDate(d));}));if(existing!=null){amount.setText(decimal(existing.amountCents));method.setText(existing.method);note.setText(existing.note);}else{PairChoice c=choices.get(selected);method.setText(c.p.method==null?"":c.p.method);}
        pair.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){if(existing==null&&method.getText().toString().trim().isEmpty()){String m=choices.get(pos).p.method;method.setText(m==null?"":m);}}public void onNothingSelected(android.widget.AdapterView<?> p){}});
        l.addView(pair);l.addView(amount);l.addView(dateBtn);l.addView(method);l.addView(note);
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(existing==null?"Nuovo pagamento":"Modifica pagamento").setView(l).setPositiveButton("Salva",(d,w)->{try{long cents=parseMoney(amount.getText().toString());if(cents<=0)throw new Exception();PairChoice c=choices.get(pair.getSelectedItemPosition());if(existing==null)db.addPayment(c.s.id,c.p.id,cents,date[0],method.getText().toString().trim(),note.getText().toString().trim());else db.updatePayment(existing.id,c.s.id,c.p.id,cents,date[0],method.getText().toString().trim(),note.getText().toString().trim());afterSave.run();}catch(Exception e){toast("Importo non valido");}}).setNegativeButton("Annulla",null);
        if(existing!=null)b.setNeutralButton("Elimina",(d,w)->confirm("Eliminare il pagamento?","Quote, arretrati e anticipi verranno ricalcolati automaticamente.",()->{db.deletePayment(existing.id);afterSave.run();}));b.show();
    }

    private LinearLayout form(){LinearLayout l=vbox();l.setPadding(dp(18),dp(4),dp(18),0);return l;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(16);e.setSingleLine(false);e.setPadding(dp(2),dp(12),dp(2),dp(12));e.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{ACCENT,Color.rgb(70,70,78)}));return e;}
    private CheckBox check(String text,boolean checked){CheckBox c=new CheckBox(this);c.setText(text);c.setTextColor(TEXT);c.setTextSize(15);c.setChecked(checked);c.setPadding(0,dp(10),0,dp(10));c.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,MUTED}));return c;}
    private Spinner spinner(){Spinner s=new Spinner(this);s.setPadding(0,dp(8),0,dp(8));return s;}
    private Button dateButton(String label,LocalDate date){Button b=button(label+" · "+longDate(date),false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(8),0,dp(8));b.setLayoutParams(p);return b;}
    private void pickDate(LocalDate initial,java.util.function.Consumer<LocalDate> done){LocalDate d=initial==null?LocalDate.now():initial;new DatePickerDialog(this,(v,y,m,day)->done.accept(LocalDate.of(y,m+1,day)),d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()).show();}

    private void exportBackup(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"quotabuddy-backup-"+LocalDate.now()+".json");startActivityForResult(i,EXPORT_BACKUP);}
    private void chooseBackup(){confirm("Ripristinare un backup?","I dati attuali verranno sostituiti dal contenuto del file scelto.",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT_BACKUP);});}
    private void writeBackup(Uri uri){try(PrintWriter w=new PrintWriter(getContentResolver().openOutputStream(uri))){w.print(db.exportJson().toString(2));toast("Backup esportato");}catch(Exception e){toast("Errore durante l'esportazione");}}
    private void readBackup(Uri uri){try(BufferedReader br=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))){StringBuilder s=new StringBuilder();String line;while((line=br.readLine())!=null)s.append(line).append('\n');String text=s.toString().trim();if(text.startsWith("{"))db.importJson(new JSONObject(text));else db.importLegacyText(text);toast("Backup ripristinato");showHome();}catch(Exception e){toast("Backup non valido: "+e.getMessage());}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;if(request==EXPORT_BACKUP)writeBackup(data.getData());else if(request==IMPORT_BACKUP)readBackup(data.getData());}

    private void confirm(String title,String message,Runnable yes){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Conferma",(d,w)->yes.run()).setNegativeButton("Annulla",null).show();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    private long parseMoney(String s){if(s==null||s.trim().isEmpty())throw new IllegalArgumentException();String clean=s.replace("€","").replace(" ","");if(clean.contains(","))clean=clean.replace(".","").replace(',','.');BigDecimal x=new BigDecimal(clean).setScale(2,RoundingMode.HALF_UP);return x.movePointRight(2).longValueExact();}
    private String decimal(long cents){return String.format(Locale.ITALY,"%.2f",cents/100.0);}
    private String eur(long cents){return "€ "+decimal(cents);}
    private String shortDate(LocalDate d){if(d==null)return "—";return d.getDayOfMonth()+" "+d.getMonth().getDisplayName(TextStyle.SHORT,Locale.ITALY).replace(".","");}
    private String longDate(LocalDate d){if(d==null)return "—";return d.getDayOfMonth()+" "+d.getMonth().getDisplayName(TextStyle.FULL,Locale.ITALY)+" "+d.getYear();}
    private String monthYear(LocalDate d){if(d==null)return "—";String m=d.getMonth().getDisplayName(TextStyle.FULL,Locale.ITALY);return m.substring(0,1).toUpperCase(Locale.ITALY)+m.substring(1)+" "+d.getYear();}
}
