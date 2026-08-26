package com.quotabuddy.app;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Ledger {
    private final Db db;
    public Ledger(Db db){this.db=db;}

    public LocalDate cycleOnOrAfter(Models.Subscription s, LocalDate date){
        if(date.isBefore(s.startDate) || date.equals(s.startDate)) return s.startDate;
        LocalDate c=cycleInMonth(s,date.getYear(),date.getMonthValue());
        if(c.isBefore(date))c=nextCycle(s,c);
        if(c.isBefore(s.startDate))c=s.startDate;
        return c;
    }

    public LocalDate cycleOnOrBefore(Models.Subscription s,LocalDate date){
        if(date.isBefore(s.startDate))return null;
        LocalDate c=cycleInMonth(s,date.getYear(),date.getMonthValue());
        if(c.isAfter(date))c=previousCycle(s,c);
        return c.isBefore(s.startDate)?null:c;
    }

    public LocalDate nextCycle(Models.Subscription s,LocalDate cycle){YearMonth ym=YearMonth.from(cycle).plusMonths(1);return LocalDate.of(ym.getYear(),ym.getMonth(),Math.min(s.renewalDay,ym.lengthOfMonth()));}
    public LocalDate previousCycle(Models.Subscription s,LocalDate cycle){YearMonth ym=YearMonth.from(cycle).minusMonths(1);return LocalDate.of(ym.getYear(),ym.getMonth(),Math.min(s.renewalDay,ym.lengthOfMonth()));}
    private LocalDate cycleInMonth(Models.Subscription s,int year,int month){YearMonth ym=YearMonth.of(year,month);return LocalDate.of(year,month,Math.min(s.renewalDay,ym.lengthOfMonth()));}

    public boolean isActive(Models.Membership m,LocalDate cycle){return !cycle.isBefore(m.joinedOn)&&(m.leftOn==null||cycle.isBefore(m.leftOn));}

    public int activeMemberCount(long subId,LocalDate cycle){int n=0;for(Models.Membership m:db.membershipsForSubscription(subId))if(isActive(m,cycle))n++;return n;}

    public long memberShareAt(Models.Subscription s,LocalDate cycle){long total=db.priceAt(s.id,cycle);int members=activeMemberCount(s.id,cycle);int divisor=members+(s.includeOwner?1:0);if(divisor<=0)return 0;return Math.round(total/(double)divisor);}

    public Models.MemberSnapshot snapshot(Models.Membership m,LocalDate today){
        Models.MemberSnapshot out=new Models.MemberSnapshot();out.membership=m;out.person=db.person(m.personId);out.subscription=db.subscription(m.subscriptionId);
        Models.Subscription s=out.subscription;if(s==null||out.person==null)return out;
        long paid=db.totalPaid(s.id,m.personId);out.totalPaidCents=paid;
        LocalDate current=cycleOnOrBefore(s,today);out.currentShareCents=current==null?0:memberShareAt(s,current);
        long cumulative=0,dueToday=0;LocalDate lastFully=null,firstUncovered=null;long firstCharge=0,firstCovered=0,firstRemain=0;
        LocalDate cycle=s.startDate;int guard=0;
        while(guard++<360){
            if(isActive(m,cycle)){
                long charge=memberShareAt(s,cycle);
                long before=cumulative;cumulative+=charge;
                if(!cycle.isAfter(today))dueToday=cumulative;
                if(paid>=cumulative)lastFully=cycle;
                else if(firstUncovered==null){firstUncovered=cycle;firstCharge=charge;firstCovered=Math.max(0,paid-before);firstRemain=Math.max(0,charge-firstCovered);if(cycle.isAfter(today)||paid<cumulative)break;}
            }
            if(m.leftOn!=null&&!cycle.isBefore(m.leftOn)&&cycle.isAfter(today))break;
            cycle=nextCycle(s,cycle);
        }
        // dueToday must include all active cycles through today even if first uncovered happened earlier.
        dueToday=chargesThrough(m,today);
        out.dueThroughTodayCents=dueToday;out.balanceCents=paid-dueToday;out.outstandingCents=Math.max(0,-out.balanceCents);out.advanceCents=Math.max(0,out.balanceCents);
        out.coveredThrough=lastFully;out.nextChargeDate=firstUncovered;out.nextChargeCents=firstCharge;out.nextChargeAlreadyCoveredCents=firstCovered;out.nextChargeRemainingCents=firstRemain;
        if(today.isBefore(m.joinedOn))out.state=Models.MemberState.UPCOMING;
        else if(m.leftOn!=null&&!today.isBefore(m.leftOn)&&out.outstandingCents==0)out.state=Models.MemberState.ENDED;
        else if(out.outstandingCents>0)out.state=Models.MemberState.LATE;
        else if(out.advanceCents>0)out.state=Models.MemberState.CREDIT;
        else out.state=Models.MemberState.PAID;
        return out;
    }

    public long chargesThrough(Models.Membership m,LocalDate date){Models.Subscription s=db.subscription(m.subscriptionId);if(s==null||date.isBefore(s.startDate))return 0;long sum=0;LocalDate cycle=s.startDate;int guard=0;while(!cycle.isAfter(date)&&guard++<360){if(isActive(m,cycle))sum+=memberShareAt(s,cycle);cycle=nextCycle(s,cycle);}return sum;}

    public List<Models.MemberSnapshot> snapshots(long subId,LocalDate today){ArrayList<Models.MemberSnapshot> out=new ArrayList<>();for(Models.Membership m:db.membershipsForSubscription(subId))out.add(snapshot(m,today));out.sort(Comparator.comparing((Models.MemberSnapshot x)->stateRank(x.state)).thenComparing(x->x.person==null?"":x.person.name,String.CASE_INSENSITIVE_ORDER));return out;}
    private int stateRank(Models.MemberState s){if(s==Models.MemberState.LATE)return 0;if(s==Models.MemberState.PAID)return 1;if(s==Models.MemberState.CREDIT)return 2;if(s==Models.MemberState.UPCOMING)return 3;return 4;}

    public Models.SubscriptionSummary summary(Models.Subscription s,LocalDate today){Models.SubscriptionSummary x=new Models.SubscriptionSummary();x.subscription=s;LocalDate current=cycleOnOrBefore(s,today);if(current==null)current=s.startDate;x.currentPriceCents=db.priceAt(s.id,current);x.activeMembers=activeMemberCount(s.id,current);x.memberShareCents=memberShareAt(s,current);x.expectedFromMembersCents=x.memberShareCents*x.activeMembers;for(Models.MemberSnapshot ms:snapshots(s.id,today)){x.outstandingCents+=ms.outstandingCents;x.advanceCents+=ms.advanceCents;if(ms.state==Models.MemberState.LATE)x.lateMembers++;}LocalDate next=cycleOnOrAfter(s,today);if(next!=null&&!next.isAfter(today))next=nextCycle(s,next);x.nextRenewal=next;return x;}
}
