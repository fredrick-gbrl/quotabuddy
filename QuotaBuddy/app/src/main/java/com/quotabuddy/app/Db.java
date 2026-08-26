package com.quotabuddy.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Db extends SQLiteOpenHelper {
    private static final String DB_NAME = "quotabuddy.db";
    private static final int DB_VERSION = 2;

    public Db(Context c) { super(c, DB_NAME, null, DB_VERSION); }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) { createSchema(db); }

    private void createSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS people(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL COLLATE NOCASE," +
                "method TEXT," +
                "note TEXT," +
                "created_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS subscriptions(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "start_date TEXT NOT NULL," +
                "renewal_day INTEGER NOT NULL," +
                "include_owner INTEGER NOT NULL DEFAULT 1," +
                "archived INTEGER NOT NULL DEFAULT 0," +
                "created_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS subscription_prices(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "subscription_id INTEGER NOT NULL," +
                "amount_cents INTEGER NOT NULL," +
                "valid_from TEXT NOT NULL," +
                "FOREIGN KEY(subscription_id) REFERENCES subscriptions(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_prices_sub_date ON subscription_prices(subscription_id,valid_from)");
        db.execSQL("CREATE TABLE IF NOT EXISTS memberships(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "subscription_id INTEGER NOT NULL," +
                "person_id INTEGER NOT NULL," +
                "joined_on TEXT NOT NULL," +
                "left_on TEXT," +
                "note TEXT," +
                "FOREIGN KEY(subscription_id) REFERENCES subscriptions(id) ON DELETE CASCADE," +
                "FOREIGN KEY(person_id) REFERENCES people(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memberships_sub ON memberships(subscription_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memberships_person ON memberships(person_id)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_memberships_unique ON memberships(subscription_id,person_id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS payments(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "subscription_id INTEGER NOT NULL," +
                "person_id INTEGER NOT NULL," +
                "amount_cents INTEGER NOT NULL," +
                "paid_at TEXT NOT NULL," +
                "method TEXT," +
                "note TEXT," +
                "created_at TEXT NOT NULL," +
                "FOREIGN KEY(subscription_id) REFERENCES subscriptions(id) ON DELETE CASCADE," +
                "FOREIGN KEY(person_id) REFERENCES people(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_payments_sub_person ON payments(subscription_id,person_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_payments_date ON payments(paid_at)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) migrateV1(db);
    }

    private void migrateV1(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL("ALTER TABLE people RENAME TO people_v1");
            db.execSQL("ALTER TABLE payments RENAME TO payments_v1");
            createSchema(db);

            class OldPerson { long oldId, newId; String name, method, category; long share; int due; }
            List<OldPerson> oldPeople = new ArrayList<>();
            Cursor pc = db.rawQuery("SELECT id,name,amount,due_day,method,category FROM people_v1 ORDER BY id", null);
            while (pc.moveToNext()) {
                OldPerson p = new OldPerson();
                p.oldId = pc.getLong(0); p.name = pc.getString(1);
                p.share = Math.round(pc.getDouble(2) * 100.0); p.due = pc.getInt(3);
                p.method = pc.getString(4); p.category = pc.getString(5);
                ContentValues pv = new ContentValues();
                pv.put("name", p.name); pv.put("method", p.method); pv.put("note", "Importato da QuotaBuddy 1"); pv.put("created_at", now());
                p.newId = db.insert("people", null, pv);
                oldPeople.add(p);
            }
            pc.close();

            Map<Long, String> earliestPayment = new LinkedHashMap<>();
            Cursor ep = db.rawQuery("SELECT person_id,MIN(paid_at) FROM payments_v1 GROUP BY person_id", null);
            while (ep.moveToNext()) earliestPayment.put(ep.getLong(0), ep.getString(1));
            ep.close();

            Map<String, List<OldPerson>> groups = new LinkedHashMap<>();
            for (OldPerson p : oldPeople) {
                String key = p.category == null || p.category.trim().isEmpty() ? "Abbonamento importato" : p.category.trim();
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
            }

            Map<Long, Long> oldPersonToSub = new LinkedHashMap<>();
            for (Map.Entry<String, List<OldPerson>> e : groups.entrySet()) {
                List<OldPerson> members = e.getValue();
                OldPerson first = members.get(0);
                int due = Math.max(1, Math.min(28, first.due));
                LocalDate start = null;
                for (OldPerson p : members) {
                    String s = earliestPayment.get(p.oldId);
                    if (s != null) {
                        try {
                            LocalDate d = LocalDate.parse(s);
                            LocalDate cyc = LocalDate.of(d.getYear(), d.getMonth(), Math.min(due, d.lengthOfMonth()));
                            if (cyc.isAfter(d)) cyc = cyc.minusMonths(1);
                            if (start == null || cyc.isBefore(start)) start = cyc;
                        } catch (Exception ignored) {}
                    }
                }
                if (start == null) {
                    LocalDate t = LocalDate.now();
                    start = LocalDate.of(t.getYear(), t.getMonth(), Math.min(due, t.lengthOfMonth()));
                }
                ContentValues sv = new ContentValues();
                sv.put("name", e.getKey()); sv.put("start_date", start.toString()); sv.put("renewal_day", due);
                sv.put("include_owner", 0); sv.put("archived", 0); sv.put("created_at", now());
                long subId = db.insert("subscriptions", null, sv);
                long averageShare = 0;
                for (OldPerson p : members) averageShare += p.share;
                averageShare = members.isEmpty() ? 0 : Math.round(averageShare / (double) members.size());
                ContentValues prv = new ContentValues();
                prv.put("subscription_id", subId); prv.put("amount_cents", averageShare * members.size()); prv.put("valid_from", start.toString());
                db.insert("subscription_prices", null, prv);
                for (OldPerson p : members) {
                    ContentValues mv = new ContentValues(); mv.put("subscription_id", subId); mv.put("person_id", p.newId);
                    mv.put("joined_on", start.toString()); mv.putNull("left_on"); mv.put("note", "Importato da QuotaBuddy 1");
                    db.insert("memberships", null, mv);
                    oldPersonToSub.put(p.oldId, subId);
                }
            }

            Map<Long, OldPerson> oldMap = new LinkedHashMap<>();
            for (OldPerson p : oldPeople) oldMap.put(p.oldId, p);
            Cursor tx = db.rawQuery("SELECT person_id,amount,paid_at,method,note FROM payments_v1 ORDER BY id", null);
            while (tx.moveToNext()) {
                long oldPid = tx.getLong(0); OldPerson p = oldMap.get(oldPid); Long sid = oldPersonToSub.get(oldPid);
                if (p == null || sid == null) continue;
                ContentValues v = new ContentValues();
                v.put("subscription_id", sid); v.put("person_id", p.newId); v.put("amount_cents", Math.round(tx.getDouble(1) * 100.0));
                v.put("paid_at", tx.getString(2)); v.put("method", tx.getString(3)); v.put("note", tx.getString(4)); v.put("created_at", now());
                db.insert("payments", null, v);
            }
            tx.close();
            db.execSQL("DROP TABLE payments_v1");
            db.execSQL("DROP TABLE people_v1");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static String now() { return LocalDateTime.now().toString(); }

    public long addPerson(String name, String method, String note) {
        ContentValues v = new ContentValues(); v.put("name", name.trim()); v.put("method", method); v.put("note", note); v.put("created_at", now());
        return getWritableDatabase().insert("people", null, v);
    }

    public void updatePerson(long id, String name, String method, String note) {
        ContentValues v = new ContentValues(); v.put("name", name.trim()); v.put("method", method); v.put("note", note);
        getWritableDatabase().update("people", v, "id=?", new String[]{String.valueOf(id)});
    }

    public boolean deletePerson(long id) {
        return getWritableDatabase().delete("people", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public Models.Person person(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT id,name,method,note FROM people WHERE id=?", new String[]{String.valueOf(id)});
        Models.Person p = null; if (c.moveToFirst()) p = readPerson(c); c.close(); return p;
    }

    public Models.Person findPersonByName(String name) {
        Cursor c = getReadableDatabase().rawQuery("SELECT id,name,method,note FROM people WHERE name=? COLLATE NOCASE LIMIT 1", new String[]{name});
        Models.Person p = null; if (c.moveToFirst()) p = readPerson(c); c.close(); return p;
    }

    public List<Models.Person> people() {
        ArrayList<Models.Person> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,name,method,note FROM people ORDER BY name COLLATE NOCASE", null);
        while (c.moveToNext()) out.add(readPerson(c)); c.close(); return out;
    }

    private Models.Person readPerson(Cursor c) {
        Models.Person p = new Models.Person(); p.id=c.getLong(0); p.name=c.getString(1); p.method=c.getString(2); p.note=c.getString(3); return p;
    }

    public long addSubscription(String name, long initialPriceCents, LocalDate firstCycle, boolean includeOwner) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ContentValues v = new ContentValues(); v.put("name", name.trim()); v.put("start_date", firstCycle.toString());
            v.put("renewal_day", firstCycle.getDayOfMonth()); v.put("include_owner", includeOwner?1:0); v.put("archived",0); v.put("created_at",now());
            long id = db.insert("subscriptions", null, v);
            ContentValues p = new ContentValues(); p.put("subscription_id",id); p.put("amount_cents",initialPriceCents); p.put("valid_from",firstCycle.toString());
            db.insert("subscription_prices", null, p); db.setTransactionSuccessful(); return id;
        } finally { db.endTransaction(); }
    }

    public void updateSubscription(long id, String name, boolean includeOwner) {
        ContentValues v = new ContentValues(); v.put("name",name.trim()); v.put("include_owner",includeOwner?1:0);
        getWritableDatabase().update("subscriptions",v,"id=?",new String[]{String.valueOf(id)});
    }

    public void archiveSubscription(long id, boolean archived) {
        ContentValues v=new ContentValues();v.put("archived",archived?1:0);getWritableDatabase().update("subscriptions",v,"id=?",new String[]{String.valueOf(id)});
    }

    public boolean deleteSubscription(long id) { return getWritableDatabase().delete("subscriptions","id=?",new String[]{String.valueOf(id)})>0; }

    public Models.Subscription subscription(long id) {
        Cursor c=getReadableDatabase().rawQuery("SELECT id,name,start_date,renewal_day,include_owner,archived FROM subscriptions WHERE id=?",new String[]{String.valueOf(id)});
        Models.Subscription s=null;if(c.moveToFirst())s=readSubscription(c);c.close();return s;
    }

    public List<Models.Subscription> subscriptions(boolean includeArchived) {
        ArrayList<Models.Subscription> out=new ArrayList<>();
        Cursor c=getReadableDatabase().rawQuery("SELECT id,name,start_date,renewal_day,include_owner,archived FROM subscriptions "+(includeArchived?"":"WHERE archived=0 ")+"ORDER BY archived,name COLLATE NOCASE",null);
        while(c.moveToNext())out.add(readSubscription(c));c.close();return out;
    }

    private Models.Subscription readSubscription(Cursor c){ Models.Subscription s=new Models.Subscription();s.id=c.getLong(0);s.name=c.getString(1);s.startDate=LocalDate.parse(c.getString(2));s.renewalDay=c.getInt(3);s.includeOwner=c.getInt(4)==1;s.archived=c.getInt(5)==1;return s; }

    public long addPrice(long subscriptionId,long amountCents,LocalDate validFrom){ContentValues v=new ContentValues();v.put("subscription_id",subscriptionId);v.put("amount_cents",amountCents);v.put("valid_from",validFrom.toString());return getWritableDatabase().insert("subscription_prices",null,v);}
    public void deletePrice(long priceId){getWritableDatabase().delete("subscription_prices","id=?",new String[]{String.valueOf(priceId)});}
    public List<Models.PricePoint> prices(long subId){ArrayList<Models.PricePoint> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,subscription_id,amount_cents,valid_from FROM subscription_prices WHERE subscription_id=? ORDER BY valid_from",new String[]{String.valueOf(subId)});while(c.moveToNext()){Models.PricePoint p=new Models.PricePoint();p.id=c.getLong(0);p.subscriptionId=c.getLong(1);p.amountCents=c.getLong(2);p.validFrom=LocalDate.parse(c.getString(3));out.add(p);}c.close();return out;}
    public long priceAt(long subId,LocalDate cycle){Cursor c=getReadableDatabase().rawQuery("SELECT amount_cents FROM subscription_prices WHERE subscription_id=? AND valid_from<=? ORDER BY valid_from DESC,id DESC LIMIT 1",new String[]{String.valueOf(subId),cycle.toString()});long x=0;if(c.moveToFirst())x=c.getLong(0);c.close();return x;}

    public long addMembership(long subId,long personId,LocalDate joinedOn,String note){ContentValues v=new ContentValues();v.put("subscription_id",subId);v.put("person_id",personId);v.put("joined_on",joinedOn.toString());v.putNull("left_on");v.put("note",note);return getWritableDatabase().insert("memberships",null,v);}
    public void updateMembership(long id,LocalDate joinedOn,LocalDate leftOn,String note){ContentValues v=new ContentValues();v.put("joined_on",joinedOn.toString());if(leftOn==null)v.putNull("left_on");else v.put("left_on",leftOn.toString());v.put("note",note);getWritableDatabase().update("memberships",v,"id=?",new String[]{String.valueOf(id)});}
    public void deleteMembership(long id){getWritableDatabase().delete("memberships","id=?",new String[]{String.valueOf(id)});}
    public Models.Membership membership(long id){Cursor c=getReadableDatabase().rawQuery("SELECT id,subscription_id,person_id,joined_on,left_on,note FROM memberships WHERE id=?",new String[]{String.valueOf(id)});Models.Membership m=null;if(c.moveToFirst())m=readMembership(c);c.close();return m;}
    public List<Models.Membership> membershipsForSubscription(long subId){ArrayList<Models.Membership> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,subscription_id,person_id,joined_on,left_on,note FROM memberships WHERE subscription_id=? ORDER BY joined_on,id",new String[]{String.valueOf(subId)});while(c.moveToNext())out.add(readMembership(c));c.close();return out;}
    public List<Models.Membership> membershipsForPerson(long personId){ArrayList<Models.Membership> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,subscription_id,person_id,joined_on,left_on,note FROM memberships WHERE person_id=? ORDER BY joined_on,id",new String[]{String.valueOf(personId)});while(c.moveToNext())out.add(readMembership(c));c.close();return out;}
    private Models.Membership readMembership(Cursor c){Models.Membership m=new Models.Membership();m.id=c.getLong(0);m.subscriptionId=c.getLong(1);m.personId=c.getLong(2);m.joinedOn=LocalDate.parse(c.getString(3));String l=c.getString(4);m.leftOn=l==null||l.isEmpty()?null:LocalDate.parse(l);m.note=c.getString(5);return m;}

    public long addPayment(long subId,long personId,long amountCents,LocalDate paidAt,String method,String note){ContentValues v=new ContentValues();v.put("subscription_id",subId);v.put("person_id",personId);v.put("amount_cents",amountCents);v.put("paid_at",paidAt.toString());v.put("method",method);v.put("note",note);v.put("created_at",now());return getWritableDatabase().insert("payments",null,v);}
    public void updatePayment(long id,long subId,long personId,long amountCents,LocalDate paidAt,String method,String note){ContentValues v=new ContentValues();v.put("subscription_id",subId);v.put("person_id",personId);v.put("amount_cents",amountCents);v.put("paid_at",paidAt.toString());v.put("method",method);v.put("note",note);getWritableDatabase().update("payments",v,"id=?",new String[]{String.valueOf(id)});}
    public void deletePayment(long id){getWritableDatabase().delete("payments","id=?",new String[]{String.valueOf(id)});}
    public Models.Payment payment(long id){Cursor c=getReadableDatabase().rawQuery(paymentSelect()+" WHERE p.id=?",new String[]{String.valueOf(id)});Models.Payment p=null;if(c.moveToFirst())p=readPayment(c);c.close();return p;}
    public List<Models.Payment> payments(){ArrayList<Models.Payment> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery(paymentSelect()+" ORDER BY p.paid_at DESC,p.id DESC",null);while(c.moveToNext())out.add(readPayment(c));c.close();return out;}
    public List<Models.Payment> paymentsFor(long subId,long personId){ArrayList<Models.Payment> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery(paymentSelect()+" WHERE p.subscription_id=? AND p.person_id=? ORDER BY p.paid_at DESC,p.id DESC",new String[]{String.valueOf(subId),String.valueOf(personId)});while(c.moveToNext())out.add(readPayment(c));c.close();return out;}
    private String paymentSelect(){return "SELECT p.id,p.subscription_id,p.person_id,p.amount_cents,p.paid_at,p.method,p.note,pe.name,s.name FROM payments p JOIN people pe ON pe.id=p.person_id JOIN subscriptions s ON s.id=p.subscription_id";}
    private Models.Payment readPayment(Cursor c){Models.Payment p=new Models.Payment();p.id=c.getLong(0);p.subscriptionId=c.getLong(1);p.personId=c.getLong(2);p.amountCents=c.getLong(3);p.paidAt=LocalDate.parse(c.getString(4));p.method=c.getString(5);p.note=c.getString(6);p.personName=c.getString(7);p.subscriptionName=c.getString(8);return p;}
    public long totalPaid(long subId,long personId){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount_cents),0) FROM payments WHERE subscription_id=? AND person_id=?",new String[]{String.valueOf(subId),String.valueOf(personId)});long x=0;if(c.moveToFirst())x=c.getLong(0);c.close();return x;}
    public long receivedBetween(LocalDate from,LocalDate to){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount_cents),0) FROM payments WHERE paid_at>=? AND paid_at<=?",new String[]{from.toString(),to.toString()});long x=0;if(c.moveToFirst())x=c.getLong(0);c.close();return x;}

    public JSONObject exportJson() throws Exception {
        JSONObject root=new JSONObject();root.put("format","QuotaBuddy");root.put("version",2);root.put("exportedAt",now());
        SQLiteDatabase db=getReadableDatabase();
        root.put("people",tableToJson(db,"SELECT id,name,method,note,created_at FROM people ORDER BY id",new String[]{"id","name","method","note","created_at"}));
        root.put("subscriptions",tableToJson(db,"SELECT id,name,start_date,renewal_day,include_owner,archived,created_at FROM subscriptions ORDER BY id",new String[]{"id","name","start_date","renewal_day","include_owner","archived","created_at"}));
        root.put("prices",tableToJson(db,"SELECT id,subscription_id,amount_cents,valid_from FROM subscription_prices ORDER BY id",new String[]{"id","subscription_id","amount_cents","valid_from"}));
        root.put("memberships",tableToJson(db,"SELECT id,subscription_id,person_id,joined_on,left_on,note FROM memberships ORDER BY id",new String[]{"id","subscription_id","person_id","joined_on","left_on","note"}));
        root.put("payments",tableToJson(db,"SELECT id,subscription_id,person_id,amount_cents,paid_at,method,note,created_at FROM payments ORDER BY id",new String[]{"id","subscription_id","person_id","amount_cents","paid_at","method","note","created_at"}));
        return root;
    }

    private JSONArray tableToJson(SQLiteDatabase db,String sql,String[] cols)throws Exception{JSONArray a=new JSONArray();Cursor c=db.rawQuery(sql,null);while(c.moveToNext()){JSONObject o=new JSONObject();for(int i=0;i<cols.length;i++){if(c.isNull(i))o.put(cols[i],JSONObject.NULL);else if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER)o.put(cols[i],c.getLong(i));else o.put(cols[i],c.getString(i));}a.put(o);}c.close();return a;}

    public void importJson(JSONObject root) throws Exception {
        if(!"QuotaBuddy".equals(root.optString("format"))||root.optInt("version",0)<2)throw new IllegalArgumentException("Formato non supportato");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{clearAll(db);insertJsonRows(db,"people",root.getJSONArray("people"));insertJsonRows(db,"subscriptions",root.getJSONArray("subscriptions"));insertJsonRows(db,"subscription_prices",root.getJSONArray("prices"));insertJsonRows(db,"memberships",root.getJSONArray("memberships"));insertJsonRows(db,"payments",root.getJSONArray("payments"));db.setTransactionSuccessful();}finally{db.endTransaction();}
    }

    private void insertJsonRows(SQLiteDatabase db,String table,JSONArray rows)throws Exception{for(int i=0;i<rows.length();i++){JSONObject o=rows.getJSONObject(i);ContentValues v=new ContentValues();JSONArray names=o.names();if(names==null)continue;for(int j=0;j<names.length();j++){String k=names.getString(j);if(o.isNull(k))v.putNull(k);else{Object x=o.get(k);if(x instanceof Number)v.put(k,((Number)x).longValue());else v.put(k,String.valueOf(x));}}db.insertOrThrow(table,null,v);}}

    public void importLegacyText(String text) throws Exception {
        String[] lines=text.split("\\r?\\n");
        class LP{String name,method,cat;long share;int due;long newId;}
        List<LP> ps=new ArrayList<>();List<String[]> txs=new ArrayList<>();
        for(String line:lines){String[] p=line.split("\\|",-1);if(p.length>=6&&"P".equals(p[0])){LP x=new LP();x.name=unesc(p[1]);x.share=Math.round(Double.parseDouble(p[2])*100.0);x.due=Integer.parseInt(p[3]);x.method=unesc(p[4]);x.cat=unesc(p[5]);ps.add(x);}else if(p.length>=6&&"T".equals(p[0]))txs.add(p);}
        if(ps.isEmpty())throw new IllegalArgumentException("Nessuna persona nel backup");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{clearAll(db);for(LP p:ps){ContentValues v=new ContentValues();v.put("name",p.name);v.put("method",p.method);v.put("note","Importato da QuotaBuddy 1");v.put("created_at",now());p.newId=db.insertOrThrow("people",null,v);}Map<String,List<LP>> groups=new LinkedHashMap<>();for(LP p:ps){String k=p.cat==null||p.cat.trim().isEmpty()?"Abbonamento importato":p.cat.trim();groups.computeIfAbsent(k,z->new ArrayList<>()).add(p);}Map<String,LP> byName=new LinkedHashMap<>();for(LP p:ps)byName.put(p.name.toLowerCase(Locale.ROOT),p);Map<String,LocalDate> firstPaymentByCat=new LinkedHashMap<>();for(String[] t:txs){LP p=byName.get(unesc(t[1]).toLowerCase(Locale.ROOT));if(p==null)continue;String cat=p.cat==null||p.cat.trim().isEmpty()?"Abbonamento importato":p.cat.trim();try{LocalDate d=LocalDate.parse(unesc(t[3]));LocalDate old=firstPaymentByCat.get(cat);if(old==null||d.isBefore(old))firstPaymentByCat.put(cat,d);}catch(Exception ignored){}}Map<String,Long> subByCat=new LinkedHashMap<>();LocalDate today=LocalDate.now();for(Map.Entry<String,List<LP>>e:groups.entrySet()){List<LP> ms=e.getValue();int due=Math.max(1,Math.min(28,ms.get(0).due));LocalDate seed=firstPaymentByCat.getOrDefault(e.getKey(),today);LocalDate start=LocalDate.of(seed.getYear(),seed.getMonth(),Math.min(due,seed.lengthOfMonth()));if(start.isAfter(seed))start=start.minusMonths(1);ContentValues s=new ContentValues();s.put("name",e.getKey());s.put("start_date",start.toString());s.put("renewal_day",due);s.put("include_owner",0);s.put("archived",0);s.put("created_at",now());long sid=db.insertOrThrow("subscriptions",null,s);subByCat.put(e.getKey(),sid);long avg=0;for(LP p:ms)avg+=p.share;avg=Math.round(avg/(double)ms.size());ContentValues pr=new ContentValues();pr.put("subscription_id",sid);pr.put("amount_cents",avg*ms.size());pr.put("valid_from",start.toString());db.insertOrThrow("subscription_prices",null,pr);for(LP p:ms){ContentValues m=new ContentValues();m.put("subscription_id",sid);m.put("person_id",p.newId);m.put("joined_on",start.toString());m.putNull("left_on");m.put("note","Importato da QuotaBuddy 1");db.insertOrThrow("memberships",null,m);}}
            for(String[] t:txs){String name=unesc(t[1]);LP p=byName.get(name.toLowerCase(Locale.ROOT));if(p==null)continue;String cat=p.cat==null||p.cat.trim().isEmpty()?"Abbonamento importato":p.cat.trim();Long sid=subByCat.get(cat);if(sid==null)continue;ContentValues v=new ContentValues();v.put("subscription_id",sid);v.put("person_id",p.newId);v.put("amount_cents",Math.round(Double.parseDouble(t[2])*100.0));v.put("paid_at",unesc(t[3]));v.put("method",unesc(t[4]));v.put("note",unesc(t[5]));v.put("created_at",now());db.insertOrThrow("payments",null,v);}db.setTransactionSuccessful();}finally{db.endTransaction();}
    }

    private static String unesc(String s){return s.replace("%7C","|").replace("%25","%");}
    private void clearAll(SQLiteDatabase db){db.delete("payments",null,null);db.delete("memberships",null,null);db.delete("subscription_prices",null,null);db.delete("subscriptions",null,null);db.delete("people",null,null);}
}
