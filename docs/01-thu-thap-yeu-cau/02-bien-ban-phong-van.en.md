# Requirements elicitation transcript (raw, English)

> BA: Claude · Client (simulated by Codex): Mai Anh, owner of Khói Bếp (3 outlets, HCMC).
> Claude and Codex communicate in English by request. Vietnamese summaries: `03-tom-tat-phong-van.md`.

## Session 0 — Initial request from client


Hi, I’m Mai Anh. I run three Khói Bếp restaurants in Ho Chi Minh City. We have a POS at each place, but my team still uses paper for orders and Excel to put the numbers together. When it gets busy, the kitchen sometimes misses an added dish, and I usually don’t have a clear picture of the three shops until the next day.

I’m looking for software that makes service smoother and lets me see what’s happening with sales and stock across the shops. I’d like to try it in one restaurant first, then roll it out to the others before next April if it works well. Could we talk about what that would involve?

---

## Session 1 — Business context, goals, constraints (owner Mai Anh)

### BA questions

Thanks, Mai Anh. Before we talk about software I'd like to understand the business itself, so today is mostly about how things work now and what you want to achieve. Please answer each numbered question in character, in English. If something is outside what you personally know, say so or tell me who knows ("my head chef would know"), and feel free to bring in a staff member's view. Around 800-1200 words in total is fine.

1. Tell me about Khói Bếp: the concept, the menu style (roughly how many dishes, any set menus, combos, grill items sold by weight, drinks/beer), and why customers come to you.
2. For each of the three restaurants: district, number of tables/seats and zones (indoor, outdoor, private rooms), opening hours, typical number of customers on a weekday vs. a weekend, average spend per person, and the rough split between dine-in, takeaway and delivery apps.
3. Who works in the business? Please describe the roles at head office (if there is one) and at each restaurant, roughly how many people in each role, and who decides what (menu prices, discounts, purchasing, hiring, cash handling).
4. Walk me through a normal dinner service at your busiest restaurant, from the moment a group walks in until they leave: who does what, and which tools are used at each step (POS, paper, Zalo, anything else)?
5. You mentioned a POS at each restaurant but paper for orders. Which POS is it, what exactly is it used for today, and why do staff still use paper?
6. What gets put together in Excel, by whom, when, and from which sources? Which numbers do you trust least, and why?
7. About the kitchen missing added dishes: tell me about the last time it happened. How often does it happen in a typical week, and what does it cost you (remakes, complaints, discounts, bad reviews)?
8. Apart from service and next-day visibility, what else costs you the most money or time today? If we could fix only three problems, which three would you pick?
9. Imagine it is six months after the system is live in all three restaurants. What is different? How would you measure that it was worth the money?
10. Why before next April? Which restaurant would you use as the pilot, and why that one?
11. Do you have a budget range in mind, both for building the system and for running it every month? Are you open to buying new hardware (tablets, kitchen screens, printers)?
12. Have you tried or looked at other software before (KiotViet, Sapo, iPOS, CukCuk, ...)? What did you like, and what made you stop or not choose it?
13. Who else should I talk to? In the next sessions I'd like to meet the manager of the pilot restaurant, your head chef, a cashier, and whoever does your accounting and purchasing. Is that possible?

### Client answers

1. We’re a casual place for families and groups who want Vietnamese sharing dishes and some charcoal-grilled food. We have about 85 menu entries if you count set sizes and seasonal items. People order clay-pot rice, vegetables, grilled pork, chicken or beef, and hotpot sets. We sell beer, soft drinks and tea. Some meat comes in different portion sizes, but we don’t normally sell it by the kilo to the customer. I think people come because a group can eat well without having to agree on just one kind of food, and the atmosphere is relaxed.

2. We have three Ho Chi Minh City locations, using the old district names people still know:

   - **Bình Thạnh:** 24 tables, about 96 seats. Mostly indoors, with six tables in a covered outside area. On a normal weekday we see about 100 dine-in guests; a good weekend day might be 160–175.
   - **Phú Nhuận:** 20 tables, about 80 seats. Mostly indoors, four tables near the open front, and a section we can screen off for a group. Roughly 85–100 weekday guests and 145–160 on a strong weekend day.
   - **District 3:** 22 tables, about 88 seats, including a small private room with three of those tables and a four-table terrace. Roughly 90–110 weekday guests and 150–165 on a strong weekend day.

   All three open daily from 10:30 a.m. to 10:30 p.m.; the cooks start prep earlier. Dine-in spend is usually around VND 210,000–250,000 per person, more when a group orders beer. Very roughly, I would say 75–80% of sales are dine-in, around 5% takeaway, and the rest delivery. I’d want the accountant to check those percentages.

3. I oversee the three shops, major suppliers, budgets, and final menu prices. We have one purchasing coordinator, one accountant, and two people doing shared prep work. Each restaurant has a manager, cashier, head chef, three or four cooks, around seven regular servers and runners, and a cleaner. A server helps greet and seat people; we add part-time staff on busy evenings. That gives us a little under 50 regular people across the business, plus about seven part-timers, though the number changes.

   I approve price changes and larger promotions. Managers can solve small customer problems, but I don’t think our discount rules are written clearly enough. They arrange shifts and usually hire servers with my approval; I approve managers and chefs. Our purchasing coordinator places most supplier orders after the managers and chefs say what they need. Managers receive goods and close the cash drawer; the accountant reviews their figures. There is some disagreement here: managers want freedom to fix a bill quickly, while our accountant wants a reason recorded every time something is removed or discounted.

4. At Bình Thạnh on a busy evening, a server or manager checks whether the group booked, finds a table on our laminated floor plan, and takes them there. The first order is written on a paper slip. A cashier enters it into POS365, and a kitchen ticket is printed. The cooks work from tickets, but when it’s noisy, a server may call out a change or send a Zalo message as well. Drinks are handled separately.

   Servers carry food, check what is still coming, and write down additions. The cashier adds those to the bill. At the end, the cashier prints the bill and takes cash, checks a bank transfer, or uses the separate card terminal. Then the manager checks the table is cleared and available again. It sounds simple when I describe it slowly. At 7:30 p.m., the slips, kitchen tickets and POS can tell slightly different stories.

5. It’s POS365, with a separate account at each restaurant. We mainly use it to enter orders for billing, print kitchen tickets and bills, and export sales at day’s end. Staff keep paper because servers can write quickly while standing beside a crowded table, and some are much more comfortable with that than entering every change on a device. They also want a way to keep serving if the connection is bad. At Phú Nhuận the internet drops perhaps twice a month, often for 15–40 minutes. Staff carry on with paper and enter things afterward, which is where we can get confused about what already reached the kitchen.

6. Our accountant takes the three POS exports, bank records, card totals, and GrabFood and ShopeeFood settlement reports, then puts them together in Excel. She also tracks purchases, supplier amounts still owing, wages, and the figures managers send after closing. Each manager counts the drawer and sends a photo of the closing sheet in Zalo. On a good day I see a combined picture the next day; after a busy weekend it can take longer.

   I trust the broad sales total more than the cash-by-payment-method figure or the stock figure. A customer’s transfer may have no useful bill reference, and app payments arrive later after fees and promotions. On stock, we count beer regularly, but for meat and vegetables the quantities on paper do not always explain what was actually used. Our accountant spends about two or three hours a day matching things.

7. Last Saturday at Bình Thạnh, a table added mushrooms and another beef portion after its first hotpot order. The mushrooms arrived, but the beef addition wasn’t on the kitchen ticket. The server thought the cashier had entered it; the cashier thought the server was still confirming it. The guests waited around 20 minutes before asking. We rushed it and gave them a small discount, about VND 150,000. Across the three shops, I hear of this sort of missed or duplicated change perhaps two or three times in a week, especially on weekends. That’s my estimate, not a proper log. We’ve also wasted food on a duplicate hotpot order for a large group. Complaints are more common than bad public reviews, but a manager would have better examples.

8. My three would be: orders reaching the kitchen correctly, knowing what money we actually took and where it went, and understanding stock usage and waste. The last one is difficult. We prepare some sauces and portion meat in the Bình Thạnh back room, then send it to the other shops. The boxes and kilos are written in a notebook, but returns and waste are not always recorded. Our accountant allocates some of that cost roughly; the chefs could explain the preparation better than I can. We found a beer stock gap of about VND 4.8 million over two weeks in one check. I don’t know its cause, and I don’t want to blame someone without evidence.

9. Six months after rollout, I’d like to see that evening’s sales and payments for each shop before I go to bed, with differences clearly explained. The accountant should spend less than an hour a day matching payments instead of two or three. I’d like fewer guest complaints about missed dishes, and beer and expensive meat counts much closer to what we expect. We think stock differences are around 6–8% now and would like them below 3%, but we need a proper starting measurement.

10. I’d pilot at Bình Thạnh because it is our oldest and busiest shop, and I’m there most often. If it works during a Friday or Saturday dinner there, I’ll believe it can work elsewhere. I’d like a serious pilot by late January, then the other shops by the end of March 2027. April is a busy holiday period for us, so I don’t want to be changing how everyone works then.

11. I was thinking about VND 350–500 million to build and put it into use, with monthly running and support costs ideally no more than VND 8–12 million. If there’s a strong reason, I can discuss more, but I cannot approve an unlimited project. I’m open to tablets, printers or a kitchen screen; I’d need to see the total hardware cost and let staff try the setup before buying for all three shops.

12. I’ve looked at demonstrations of Sapo and KiotViet, and I know people using iPOS and CukCuk. They look much better than what we do on paper, especially ordering and reports. I haven’t done a fair trial of each one. What worries me is whether our three shops, the shared prep work, and the accountant’s payment matching will fit without keeping several spreadsheets anyway. I’m open to being shown that an existing product can handle it.

13. Yes, that’s possible. I can arrange our Bình Thạnh manager, head chef, and cashier, plus our accountant and purchasing coordinator. Please give me some notice for the chef and cashier so we can talk outside dinner rush.

---

## Session 2 — Front of house at Bình Thạnh (manager Lan, cashier Huy)

### BA questions

Participants: the Bình Thạnh manager and the Bình Thạnh cashier (Mai Anh may drop in). Please give the manager and cashier names, answer in their own voices, and label who is speaking. If they see something differently, let them disagree. Concrete recent examples and rough numbers are more useful than general statements; "I don't know" is fine. Around 1200-1700 words in total.

Questions for the manager
M1. Reservations: how do guests book today (phone, Zalo, Facebook, walk-in)? Where is it written down? Do you take deposits for large groups or the private room? What happens with no-shows and late arrivals? Roughly how many bookings on a weekday vs. a Saturday?
M2. Seating: when the floor is full, how do you manage people waiting? How do you handle big groups that need tables joined, or a group that moves to another table mid-meal? How do you know which tables are free, waiting for the bill, or being cleaned?
M3. Ordering: what exactly is written on an order slip? How do you note special requests (less spicy, no MSG, allergies), hotpot sets and what's in them, portion sizes, and "send this later" timing? Who takes drink orders, and where are drinks prepared?
M4. Changes: what are the rules today for adding, cancelling or returning a dish before it is cooked, while it is being cooked, and after it is served? Who is allowed to approve a cancellation? Tell me about the most recent dish that was sent back.
M5. Discounts and free items: what kinds do you give (complaints, regulars, staff meals, friends of the owner, promotions, vouchers)? Who can approve which amount today, and how is it recorded?
M6. Takeaway and delivery: how do GrabFood and ShopeeFood orders arrive, who accepts them, how do they reach the kitchen, and what do you do when an item is sold out? Are app prices different from in-store prices? Who changes the menu on the apps?
M7. When the internet or POS goes down, what exactly do you do, and what goes wrong afterwards?
M8. During a Friday dinner rush, what would you like to see at a glance that you cannot see today?

Questions for the cashier
C1. Describe your desk during a shift: what do you enter into POS365, when, and from which pieces of paper?
C2. Bills: how often do guests ask to split a bill (by item, equally, per person) or to combine two tables into one bill? How do you do it now?
C3. Payments: roughly what share is cash, bank transfer, card and e-wallet? For a bank transfer, how do you confirm the money has arrived? Is it the same QR for every bill? What goes wrong?
C4. Invoices and charges: how often do customers ask for a VAT invoice with a company name and tax code, and how is it issued today (POS365, a separate e-invoice provider, the accountant later)? Do menu prices include VAT? Is there any service charge or surcharge (holidays, private room, delivery packaging)?
C5. Cash drawer: walk me through the start and end of a shift: opening float, counting, the closing sheet, who checks it. Are there shift changes during the day? What happens when the count is short or over?
C6. Unusual cases: a guest leaves without paying; a guest disputes a charge after paying; a payment taken twice; a refund. What happened the last time each occurred?
C7. Which part of your job would you most like the new system to take away? What are you afraid it would make harder?

For both
B1. Where do the floor team, the cashier and the kitchen disagree most often, and why?

### Client answers

**Lan (Bình Thạnh manager):** I’m Trần Ngọc Lan. I’ve managed this shop for nearly three years.

**Huy (cashier):** I’m Phạm Quốc Huy. I usually work the afternoon and dinner shift.

### Manager

**M1 — Reservations**

**Lan:** People call, message our Zalo number or Facebook page, or ask while they’re eating if they can come back with a group. I write bookings in a notebook: name, phone, date, time, party size, and any request. On a weekday we might have four to six bookings; Saturday can be 12–18. Walk-ins are still a big part of dinner.

For groups of eight or more, I often ask for a deposit of VND 500,000 to 2 million, especially on Saturday. Bình Thạnh doesn’t have a private room; the private room is at District 3. Usually the deposit goes to the company bank account. If Mai Anh personally arranged a booking, it has occasionally gone to her account, and I then have to tell Huy and the accountant. That arrangement causes confusion. We call a group if it is 15 minutes late. I normally hold the tables about 20 minutes on a busy night, but it depends on whether they answer. We haven’t consistently kept deposits after a no-show; I discuss those cases with Mai Anh.

**M2 — Seating**

**Lan:** We have a laminated floor plan and mark bookings on it. The host or I watch which tables are occupied, waiting for payment, or being cleaned. When the floor is full, I write the waiting group’s name and phone number on a pad and give them an approximate wait. I sometimes get that estimate wrong because a table has asked for the bill but is still talking.

For a large group we push tables together and tell Huy which table numbers belong to them. If guests move, the server writes the old and new table numbers on the slip and tells Huy. The floor plan and POS do not always get updated in the same order. Last Friday a six-person group moved away from the covered outside area when it rained; their second drink order was nearly delivered to the original table.

**M3 — Ordering**

**Lan:** The slip has table number, server initials, time, dish name or code, quantity, and a short note. Servers write “less spicy,” “no coriander,” or “no MSG” in the margin. If a guest mentions an allergy, I want the server to tell the chef directly as well; handwriting alone worries me. The chef would need to explain what we can safely promise for a serious allergy.

Hotpot sets have a menu code, so we don’t write every included item unless the guest changes something. Servers circle the portion size for grilled meat. For “send this after the first dishes,” they write “later” and keep a separate slip at the service counter. That’s easy to forget. Servers take drink orders too. Bottled drinks and beer come from the drink fridge by the service counter; tea is made there.

**M4 — Changes**

**Lan:** If the kitchen hasn’t started a dish, I’ll usually allow a cancellation. Once they’re cooking it, I ask the chef whether it can be stopped or used for another order. If it has been served, I don’t want it simply deleted from the bill. We need a reason, such as the wrong dish or a quality complaint. In practice, Huy asks me to approve most removals, but we don’t have a proper written rule.

Yesterday a guest sent back grilled chicken because it was too dry. I tasted a piece and agreed. We made it again and didn’t charge for the first plate. The chef thought it was acceptable and was annoyed I had promised a replacement before speaking to him. We settled it, but those decisions can be tense during a rush.

**M5 — Discounts and free items**

**Lan:** We give small discounts for a long wait or a complaint, occasional vouchers, and sometimes something complimentary for regular guests. Staff meals are recorded on a separate sheet; they shouldn’t appear as customer discounts. If Mai Anh wants to treat a friend, she usually messages me.

I can normally decide on a small dessert or about 5–10% off to solve a problem. For more, I call Mai Anh. That is how we behave, not a policy everyone has signed. On the POS, the reason can be too general. Huy would prefer I give him a clear instruction every time; sometimes I’m already dealing with the next table.

**M6 — Takeaway and delivery**

**Lan:** GrabFood and ShopeeFood each have a tablet. A server near the counter watches them and accepts orders, then writes or prints the details for the kitchen. Huy enters the sale in our POS so it appears in our shop figures. During dinner, that can be delayed. App prices are generally higher than dine-in prices because of fees, and some app sets differ.

If we run out of an item, I tell the counter server to mark it unavailable on both apps. We’ve missed one app before. I can change availability, but Mai Anh approves price changes. Our purchasing person sometimes tells me about a shortage late, after orders have arrived.

**M7 — Outages**

**Lan:** If the internet is down, we keep taking paper orders and mark each slip “not entered.” Kitchen work continues with paper tickets carried over by a runner. If the POS itself is unavailable, Huy writes bills by hand. When things return, Huy enters what is missing while I compare the slips with the kitchen stack. The hard part is knowing whether a slip was already printed before the failure. Phú Nhuận has the connection problem more often than we do; they had duplicate kitchen tickets after an outage in August.

**M8 — Friday rush**

**Lan:** I’d want to know which tables have been waiting longest for food, which dishes are actually cooking, and which tables are ready to turn. Right now I find out by walking to the kitchen and asking. I also need to know immediately if we’ve run out of a popular dish. A screen would help only if people keep it current.

### Cashier

**C1 — Desk and entry**

**Huy:** My desk has the POS, bill printer, cash drawer, card terminal, a phone for bank notifications, order slips, and often an app tablet that someone has left with me. I enter the first slip when it reaches the counter, then additions as servers bring them. I check the table number and try to staple the slips together. During a rush, three servers may hand me changes while someone asks to pay. That’s when I ask them to wait and confirm which table they mean.

**C2 — Bills**

**Huy:** On a Friday or Saturday, perhaps four to six groups ask to split a bill. “Half each” is easy; splitting by dish takes longer, particularly if they’ve shared a hotpot and drinks. Usually I print the item list, mark what each person claims, and make separate calculations before recording the payments. Combining two tables happens maybe twice a week, usually for a group we seated apart and later joined. If Lan tells me early, it is fine. If I hear only at payment time, I worry about missing an addition.

**C3 — Payments**

**Huy:** Roughly 25% of our value is cash, 55% bank transfer, 15% card, and 5% wallets, but that is only my impression. We show the same printed bank QR at the desk, so guests have to type the amount. I check for an incoming bank notification and the amount, not just their screenshot. Sometimes notifications are late or two guests send the same amount close together. I write the bill number beside the transfer in my closing notes if I can identify it. Mixed cash and transfer payments happen often enough that I don’t find them unusual.

**C4 — Invoices and charges**

**Huy:** Maybe four to eight customers a day ask for a company VAT invoice, more when offices have a group meal. I collect the company name, tax code and email. We use a separate e-invoice provider handled by the accountant; I pass her the details and confirm the sale, and she handles the invoice in that system. If someone notices an incorrect company detail later, she handles the correction. Our menu prices are shown as final customer prices, including applicable tax. I cannot tell you the tax treatment of every item—our accountant should answer that.

There’s no regular service charge or private-room fee here. We may have holiday menu prices announced in advance. Delivery packaging is included in the app price for most items; I’d have to check whether a few large sets have a separate charge.

**C5 — Cash drawer**

**Huy:** We start with a VND 1 million change float. There is a lunch-to-dinner handover: I count cash with the earlier cashier or Lan, and we sign the sheet. At close, I count cash, list cash paid out with receipts, and compare it with the POS cash figure. Lan checks and sends the closing sheet and photos to the accountant on Zalo. If I’m short or over, we recount and check mixed payments first. We record the difference if we cannot explain it; I don’t just change a sale to force a match.

**C6 — Unusual cases**

**Huy:** One guest left without paying last month, around VND 360,000. The server thought another person in the group had paid. Lan called the number from the reservation, and they transferred it that evening.

Last Sunday someone disputed a second beer bottle on the bill. The server checked and found it had been entered twice, so I removed it before payment with Lan’s approval. Two weeks ago a guest paid VND 680,000 by transfer, didn’t see confirmation, and then used a card. We found both payments in the bank and card records; the accountant arranged a transfer back the next day. For a refund after payment, the most recent one I remember was a VND 220,000 dish the guest complained about after settling. Lan agreed to refund it; I recorded the adjustment and our accountant checked how to reflect it in the invoice records. I don’t do those myself.

**C7 — Biggest help and worry**

**Huy:** I’d most like to stop re-entering slips and trying to match anonymous transfers at closing. I’m afraid a new system will require so many taps for an addition or a split bill that a queue forms at my desk. I also don’t want a payment marked complete merely because a guest shows a screenshot.

### Both

**B1 — Where people disagree**

**Lan:** Usually it’s whether a changed dish reached the kitchen, and whether I can promise a discount to calm a guest before checking with Huy and the chef.

**Huy:** I agree. From my side, “I told the kitchen” doesn’t tell me whether the item belongs on the bill. I need the server to bring the change to me too.

**Lan:** And from my side, when the guest is upset, I cannot leave them standing there while three people debate a VND 60,000 side dish. That’s the part we still need to work out.

---

## Session 3 — Kitchen, shared prep, purchasing, stock (head chef Đức, prep lead Thảo, purchasing Minh)

### BA questions

Participants: the Bình Thạnh head chef, one of the two shared-prep staff (or whoever leads the shared prep), and the purchasing coordinator. Please name them, answer in their voices and label who is speaking. Disagreement is welcome. Concrete recent examples and rough numbers beat general statements; "I don't know" is fine. Around 1300-1800 words in total.

Head chef
K1. Stations: which kitchen stations do you have (grill, hotpot broth, wok/stove, clay pot, cold/salad, drinks at the counter), how many cooks at each during a rush, and where do the tickets print? Who decides the order in which dishes go out?
K2. Tickets during a rush: how do you keep track of what is pending, handle "later" items, and know which dishes of one table should go out together? How is a dish marked as done, and who carries it out?
K3. Would a kitchen screen survive and help in your kitchen (heat, smoke, grease, noise, wet hands)? Would you rather have printed tickets per station, a screen, or both? What must a ticket show, and what must it never hide?
K4. Changes: how do you want to be told about a cancellation or a changed dish? At what moment is a dish "started" so that it can no longer be cancelled for free? How are remakes and wasted plates recorded now?
K5. Allergies: what can you safely promise a guest, and what should happen with an allergy note?
K6. Sold out: how do you decide a dish is out, how do you tell the floor and the delivery-app person, and how often does it happen on a weekend?
K7. Recipes and standard portions: do you have written recipes and portion weights (grams of meat per portion size, what goes into each hotpot set, sauce ratios)? Who keeps them up to date, and how closely do cooks follow them at 8 p.m. on a Saturday?
K8. Staff meals, tastings, spoilage: how are they recorded today, if at all?

Shared prep
P1. What exactly is prepared centrally (sauces, marinades, portioned meat, broth base, anything else)? Roughly how much per day, for which shops, and how is it packed and labelled (boxes, bags, kg, dates)?
P2. How do the other shops ask for items (Zalo, a fixed daily quantity, a phone call)? When and how is it delivered, by whom, and what is written in the notebook? What happens with returns, shortfalls, items that spoil in transit, or a shop that suddenly runs out mid-evening?
P3. Yields: for example, how much sellable portioned meat do you get from 1 kg of raw meat? Do you ever measure trimming losses?
P4. Whose cost is the shared prep today: Bình Thạnh's or the whole chain's? How does the accountant split it?

Purchasing coordinator
U1. Suppliers: roughly how many, for what (meat, seafood, vegetables, beer, soft drinks, dry goods, gas/charcoal), how often you order, how orders are placed, and whether prices are agreed in advance or change daily.
U2. Deciding quantities: who requests what, and how? Do you use minimum stock levels? What are the lead times? Do managers ever buy urgently with cash, and how is that recorded?
U3. Receiving: who receives deliveries at each shop and at the shared prep, what is checked (weight, count, quality, temperature, expiry), which documents come with the goods, and what happens when a delivery is short or poor?
U4. Paying suppliers: payment terms (cash on delivery, weekly, monthly), who pays, how amounts owed are tracked, and which suppliers give VAT invoices.
U5. Beer: how is it supplied (distributor, promotions, returnable crates or deposits), how is it stored and counted? Any theory about the VND 4.8 million gap?
U6. Stock counts: what is counted, how often, by whom, at what time of day, in which units? What happens when the count differs from what you expected?
U7. What frustrates you most about purchasing and stock today?

For all three
A1. If the system showed "expected usage vs. counted usage" every week, which 10-20 items would you want on that list first, and why those?

### Client answers

**Đức (Bình Thạnh head chef):** I’m Võ Thanh Đức. I run this kitchen.

**Thảo (shared-prep lead):** I’m Lê Thu Thảo. My colleague and I prepare food in the back room at Bình Thạnh for all three shops.

**Minh (purchasing coordinator):** I’m Nguyễn Quang Minh. I place most supplier orders and follow up on deliveries.

### Head chef

**K1 — Stations**

**Đức:** On a busy Friday we have four cooks plus me. Two work the grill, one handles the wok and clay pots, and one handles hotpot broth and assembly. I move between stations, check plates, and help wherever orders are piling up. Cold sides are prepared earlier, then finished by whoever is free. Servers handle bottled drinks and tea at the counter; they are not coming from my kitchen.

The printer is near the kitchen entrance, not at each station. I sort tickets and call out priorities. Usually it’s first in, first out, but if a table has children waiting or one dish would hold up the rest, I change the order. I also need to make sure we don’t send one table four hot dishes at once when two will go cold.

**K2 — Tickets**

**Đức:** Tickets go on a rail. I mark dishes with a pen as we start them, and cross them off when they leave the pass. The runner takes food from the pass and calls the table number back to me. That works when tickets are clear. During a rush, additions print apart from the original order, so I may have to find the first ticket to understand the whole table.

“Later” is the worst word on a slip. Does the guest mean in ten minutes, after the first course, or when they ask? Sometimes the server keeps that slip, sometimes it reaches me early. Last Saturday I had hotpot ingredients ready while the table was still eating its grill dishes. We held them, but that occupies space we need for other orders.

**K3 — Screen or paper**

**Đức:** I would try a screen, but not as the only thing on day one. This room is hot and greasy, and cooks have wet hands. There is also enough noise that an alert sound won’t mean much. I want a printed ticket at the station while we test it. I need to see table number, time, dish and quantity, portion size, special instructions, and whether it is an addition or a replacement. Please don’t bury “no peanuts” or “hold this dish” under several taps. And don’t make a changed item quietly disappear from my view; I need to know what was changed and whether I already started it.

**K4 — Changes and waste**

**Đức:** Tell me directly if you are cancelling something already printed. Removing it at the cashier without speaking to the kitchen does not stop a cook. “Started” depends on the dish. If meat is already on the grill, we can’t turn it back into raw stock. For a clay pot, once the ingredients are in the pot and heating, it may be too late. If ingredients are only measured out, I can often use them elsewhere.

For the dry chicken Lan mentioned yesterday, we cooked a new plate. I thought the first one was acceptable, although I understand she was dealing with an unhappy guest. We sometimes write “remake” or “waste” on a ticket, but there is no consistent count. The plate itself might be thrown away, or a cook might taste it to understand the complaint. Those are different things to me.

**K5 — Allergies**

**Đức:** A server must speak to me about an allergy before promising a dish is safe. We use shared grills, utensils, sauces and prep surfaces. I can tell the guest what ingredients we know are in a dish and sometimes prepare a simpler one separately, but I cannot promise zero contact with an allergen. Some sauces are prepared in advance, so a note such as “no MSG” cannot always be solved by leaving something out at the last minute. For a severe allergy, I would rather be honest and decline that dish than guess.

**K6 — Sold out**

**Đức:** I decide when we cannot make a dish properly and tell Lan, who should tell the servers and the app counter. Some weekends we run out of one or two specials late in the evening; common menu items should be rarer. There is a delay when I tell the floor but an app still accepts an order. I’ve had a courier waiting while we asked a customer to change a sold-out side.

**K7 — Recipes and portions**

**Đức:** We have written set contents and rough weights for the main meats. A small grilled pork portion starts at about 150 grams before cooking; the larger one is about 250 grams. Cooks use a scale during prep, so at 8 p.m. they take a portioned pack rather than weigh every plate. Vegetable sides are less exact. Broth and sauce recipes are in my notebook, and I adjust them when an ingredient changes quality.

I know the accountant wants precise *định lượng* for every dish. I can help establish sensible weights, but I don’t want every cashier or server opening my sauce recipes. Also, if a supplier’s meat needs more trimming this week, the expected amount changes. A fixed number on a report shouldn’t automatically mean a cook took something.

**K8 — Staff meals and spoilage**

**Đức:** We write staff meals on a sheet, but sometimes they are made from leftovers rather than a normal menu portion. Tasting a sauce is just part of cooking; we don’t measure each spoonful. We write down obvious spoilage or a full discarded plate when someone remembers. Small trimming and damaged vegetables are not recorded well. That is a gap, but recording every scrap during service would be hard.

### Shared prep

**P1 — What we make**

**Thảo:** We make two main marinades, three sauces, and hotpot broth base. We also trim and portion pork, chicken and some beef. It’s a small back-room operation, not a separate factory. On an ordinary day we might handle 35–50 kilograms of meat across the three shops; ahead of a busy weekend it can be more. Sauce and broth quantities vary too much for me to give one daily number.

Portioned meat goes into dated bags, then labelled tubs by shop. Sauces go in tubs marked with preparation date and destination. We write quantities in kilograms, bags or tubs depending on the item. That inconsistency annoys Minh and the accountant, but the kitchen usually understands what “two tubs” means.

**P2 — Requests and movement**

**Thảo:** Each manager sends the next day’s request in our Zalo group, usually in the afternoon. We start with a usual quantity and adjust for bookings or a promotion. A driver takes the tubs to Phú Nhuận and District 3 in the morning; their manager or cook signs our notebook. Bình Thạnh takes its share directly from the back room.

If a shop is short, we may send an extra delivery, or one shop lends stock to another with a Zalo message. Returns are uncommon for fresh meat. If a sealed sauce tub comes back within its usable period, I check it, but the notebook doesn’t always show the return. Last month District 3 reported one leaking sauce tub only after the driver had left. I knew it wasn’t usable, but I don’t know which shop’s stock figure the accountant eventually reduced.

**P3 — Yields**

**Thảo:** We do weigh raw deliveries and finished packs, but we haven’t kept a consistent yield sheet. One kilogram of raw beef might give roughly 880 grams of usable portions. Pork can be nearer 830 grams if there is a lot to trim. Please treat those as examples from my work, not guaranteed yields. I would want Đức to agree on how we count trimming that can still be used for broth or staff food.

**P4 — Cost**

**Thảo:** The room and our wages are at Bình Thạnh, but most of what we make goes to the other shops too. I record outgoing quantities. How those costs end up in each shop’s profit figure is the accountant’s question.

**Minh:** My understanding is that she allocates some shared-prep cost based on each branch’s sales. That’s quick, but a branch ordering more expensive portioned meat may not carry its real share.

### Purchasing

**U1 — Suppliers and prices**

**Minh:** We have around ten suppliers we use regularly: two for meat, one for seafood, two for vegetables, one beer distributor, one soft-drink distributor, and others for rice, dry goods, charcoal and gas. Fresh meat and vegetables arrive most mornings. Beer and dry goods are usually ordered two or three times a week.

I order mainly by Zalo or phone. Some prices are agreed for a month; vegetable and seafood prices can change much more often. I keep quotations and invoices, but the price on a delivery isn’t always the one I last discussed.

**U2 — Quantities and urgent buys**

**Minh:** Managers and chefs send requests; I compare them with what I believe is on hand and recent sales. We don’t have dependable minimum-stock numbers for most ingredients. Fresh produce can often arrive next morning. A specific cut of beef or a popular beer may need a day or two.

Managers sometimes buy urgently from a nearby shop with petty cash. They send a receipt photo to the accountant, but I might hear about the purchase only when my supplier delivery arrives and the shop says it no longer needs everything. That happened with mushrooms two weeks ago.

**U3 — Receiving**

**Minh:** At Bình Thạnh, Thảo or a cook checks prep ingredients. At the other shops, a manager and cook should check the delivery note against count or weight, quality, dates and condition before signing. For chilled meat they also check whether it arrived cold, though I cannot say they log a temperature every time. If something is short or poor, they photograph it in Zalo and I ask the supplier for replacement or a credit. Sometimes they sign first because the driver is rushing; that makes a later dispute harder.

**U4 — Paying suppliers**

**Minh:** Small urgent purchases are cash. Most regular suppliers are paid weekly or monthly by bank transfer, arranged by the accountant after I confirm their statements. She tracks what we owe in Excel. The bigger suppliers provide VAT invoices. Some small produce sellers do not provide the paperwork as neatly; the accountant can tell you exactly how she handles those purchases. I don’t approve supplier payments myself.

**U5 — Beer**

**Minh:** The distributor delivers cases, sometimes with a promotion. Empty returnable crates go back separately; that is another thing we count. Cases are stored by the drink counter, and opened bottles should be recorded through sales. We count full cases and loose bottles nightly.

About the VND 4.8 million gap: I’ve wondered whether promotional bottles, breakage, transfers between shops, or bad opening counts were missed. I have no proof. Theft is possible too, but I would not accuse anyone from that figure alone.

**U6 — Counts**

**Minh:** Beer and soft drinks are counted at closing. Premium meat is checked about twice a week, and a fuller stock count happens at month-end. Managers or cooks count; I sometimes join a month-end count. Meat is in kilograms or portion bags, beer in cases and bottles, sauces in tubs. When a number differs, we look for missing delivery notes, transfers, staff use or waste and send an explanation to the accountant. If we cannot explain it, it remains a difference on her sheet.

**U7 — Frustration**

**Minh:** I spend too much time asking, “Did you receive this, use it, or send it to another shop?” A Zalo message can answer the immediate question, but a week later it is difficult to reconstruct.

### All three

**A1 — First items to compare**

**Đức:** Beef portions, pork portions, chicken, hotpot broth base, our two main sauces, and the more expensive seafood. Those have meaningful cost or variable yield.

**Thảo:** I’d add the finished marinade and sauce tubs sent from prep, because a missing tub is easy to misunderstand as kitchen overuse.

**Minh:** Beer by brand—especially the two best sellers—plus bottled soft drinks and returnable crates. Rice and common vegetables matter too, but I’d begin with the costly items and the ones we can count reliably. I think that makes about 15 items to start.

---

## Session 4 — Money, controls, policies, operations, priorities (accountant Hạnh, owner Mai Anh)

### BA questions

Participants: the accountant (please give her name) and Mai Anh. Answer in their voices and label who is speaking. Disagreement is welcome; concrete numbers and examples beat general statements; "I don't know yet" is fine. Around 1600-2300 words in total.

Accountant
F1. Legal form and tax: is the business a company or a household business, and under which VAT method? Do all menu items carry the same VAT rate, or do some (for example beer) differ? Who decides the tax setting of a new menu item?
F2. E-invoices: which provider do you use? Do you issue an e-invoice for every sale, or only when a customer asks? How do you handle delivery-app orders, corrections, cancellations and refunds on invoices today?
F3. Money channels: one company bank account for all three shops or one per shop? Which bank(s)? Who provides the card terminals, and when does card money settle and with what fees? How often do GrabFood and ShopeeFood settle, and what do their statements show?
F4. Daily matching: walk me through what you do each morning, step by step. What does "matched" mean to you? Which differences are most common? What would a perfect nightly report per shop contain?
F5. Deposits: how are booking deposits recorded today, and what should happen when a deposit lands in Mai Anh's personal account? What happens to a deposit after a no-show or a cancellation?
F6. Petty cash: how much cash may a shop spend on urgent purchases, who approves, how is it recorded and reconciled?
F7. Suppliers: do you want the new system to track supplier invoices and amounts owed, or only record purchases and deliveries while payables stay in your own tools? Do you use accounting software (for example MISA) that should receive data from the new system, and in what form?
F8. Shared prep costs: how would you prefer to charge shared-prep output to each shop?
F9. Staff hours and pay: how are hours tracked today (paper, fingerprint machine, app) and where is pay calculated? Should this project cover it, or stay out of scope for now?
F10. Reports: which daily, weekly and monthly reports do you need, and which of them should come straight from the new system?
F11. Controls: which actions should always require a reason or an approval? How long must sales, invoice and stock records be kept? Who should be allowed to see what?

Mai Anh
O1. Discount and void policy: what limits are you comfortable giving a shop manager (amount or percentage per bill, per shift), which reasons should staff pick from, and what must come to you? How would you like to approve from your phone, and what if you don't answer within a few minutes during a rush?
O2. Deposits: which account should receive them, and what rule do you want for no-shows and late cancellations?
O3. Menu and prices: is the menu the same in all three shops? Any shop-only dishes? How do holiday price lists work (which days, which channels)? Who may change menu items, availability and prices, and do app prices differ by item?
O4. Customers: do you want a customer list, points, membership or vouchers? What do you do for regulars today? Would you contact customers via Zalo or SMS, and have customers agreed to that?
O5. Access: what should each role (server, cashier, chef, manager, purchasing, accountant, you) be able to see and do? Should a manager see other shops' figures?
O6. Your phone at night: what do you want to see before bed, and which events should alert you immediately (for example a large discount, a void after serving, a cash difference above a threshold)?
O7. Operations: how many handheld devices would the floor need at Bình Thạnh on a Saturday? Does the kitchen have space and power for station printers or screens? Do you have backup internet (4G) or power (UPS)? Any shared devices or personal phones?
O8. Support: when would you need support (dinner service ends late), how fast must a problem be fixed during service, and who on your side would own the system day to day? Any preference about where data is hosted and who owns it?
O9. Future: any plans for a fourth shop, franchising, a bigger central kitchen, table QR self-ordering, or your own online ordering in the next two years?
O10. Priorities. For each capability below, please say Must / Should / Could / Won't, separately for the Bình Thạnh pilot (late January) and the full rollout (end of March). It is fine to push things out; we cannot do everything first.
  a. Table map and live table status
  b. Servers ordering on handheld devices, going straight to kitchen and bill
  c. Kitchen tickets per station and/or kitchen screen with dish status
  d. "Later"/hold-and-fire timing for courses
  e. Voids and discounts with reasons, approval limits and an audit trail
  f. Split and merge bills, move tables
  g. Dynamic bank QR per bill with automatic payment confirmation
  h. Card and e-wallet recording, mixed payments
  i. Shift opening/closing, cash count and handover
  j. Keep working during internet loss
  k. Reservations with deposits
  l. Waiting list
  m. Delivery-app orders inside the system (typed in vs. connected)
  n. Sold-out control across floor and apps
  o. E-invoice issued from the system
  p. Recipes and standard portions, expected usage
  q. Shared-prep production and transfers to shops
  r. Purchasing: requests, supplier orders, receiving
  s. Stock counts and variance report for the first ~15 items
  t. Owner dashboard on phone the same night
  u. Payment matching (bank, card, app settlements)
  v. Central menu and price management (per shop, holiday, app prices)
  w. Customer list and loyalty
  x. Staff timekeeping and payroll
  y. Guest self-ordering by QR at the table
  z. Export to accounting software

Both
Z1. What have I not asked about that could hurt this project if we get it wrong?

### Client answers

**Hạnh (accountant):** I’m Lê Mỹ Hạnh. I handle accounting for all three shops. Mai Anh and I agree on the problems, though we don’t always agree on how much freedom a manager should have during service.

**Mai Anh (owner):** I’m here too. Hạnh can correct me when I confuse sales with money actually received.

### Accountant

**F1 — Legal form and tax**

**Hạnh:** We are one limited company, Khói Bếp Co., Ltd., with three outlets. We use the VAT credit method, not the household-business method. I maintain the tax setting for new menu items after checking what the item actually is; Mai Anh approves its selling price. Please don’t assume every line has one VAT rate. Ordinary eligible food service currently has the reduced rate, while beer and some other items need separate classification. I would have our tax adviser check the final item list before anyone loads rates into a new system.

**F2 — E-invoices**

**Hạnh:** We use MISA meInvoice separately from POS365. Our practice is to issue an electronic invoice for each completed sale, adding a buyer’s company details when requested. The cashier sends me those details. There is too much manual checking between the POS totals and invoice records today.

Delivery orders are awkward because the app’s customer payment, our sale, the app’s fees and the later bank settlement are different numbers. I use the merchant statements to check them. For a wrong buyer name, cancellation or refund, I handle the appropriate correction in meInvoice; I do not want a cashier deleting an issued invoice as though it were an unpaid restaurant bill. I’d want to confirm the current invoice process with the provider before changing it.

**F3 — Money channels**

**Hạnh:** One Vietcombank company collection account receives QR transfers for all three restaurants. Each shop displays its own printed QR, but they lead to that account. That makes the time, amount and any transfer description important. The card terminals are supplied through our bank, with a separate terminal at each outlet. Card settlement is usually the next business day, sometimes later across a holiday. I need to check our contract for the exact fee rather than give you a number from memory.

GrabFood generally pays after a few business days. ShopeeFood has its own settlement schedule. Their statements show orders, promotions, platform fees, adjustments and the net amount due, though the labels are not identical. I download both. A bank credit is not the same thing as that day’s food sales.

**F4 — Daily matching**

**Hạnh:** Each morning I first check whether all three managers sent their closing sheets and whether any shifts have an unexplained cash difference. Then I export POS sales by shop and payment type. I compare cash to the signed counts, QR sales to bank credits, and card sales to terminal records and later settlements. I add the delivery-app orders and match their eventual settlement statements. I also look at refunds, discounts and anything removed from a bill.

“Matched” means I can explain every sale and payment once, including timing differences. It does not mean forcing the bank total to equal the POS total for one calendar day. Common problems are transfers without a bill reference, a mixed payment entered under one method, an app promotion assigned to the wrong day, and a deposit counted again as new sales money.

A useful nightly view would show, per shop, sales before and after discounts, tax and invoiced amount, payments by method, opening float, cash paid out, expected and counted cash, deposits received or applied, refunds, and unresolved differences. I would still do a later check for card and app settlements.

**F5 — Deposits**

**Hạnh:** Lan writes deposits in her booking notebook and messages me the transfer. I keep a separate Excel list with the guest, date, amount and event date. When the party pays its final bill, the deposit should reduce what remains to collect; it must not become a second sale. The occasional deposit into Mai Anh’s personal account is a problem. I want it transferred promptly into the company account, with the booking identified and both transfer records retained.

For no-shows and cancellations, there is no consistently communicated policy. I will not automatically treat an unclaimed deposit as income. Mai Anh needs to decide and tell guests the terms at booking; until then I ask the manager what was promised and record the outcome.

**F6 — Petty cash**

**Hạnh:** Each shop has its VND 1 million change float, plus a small amount for approved urgent expenses. A manager will sometimes spend VND 200,000–300,000 on missing vegetables or ice and send a receipt photo. Larger expenses should be cleared with Mai Anh first, but that has sometimes happened after the purchase. I match the receipt, manager’s note, cash taken from the drawer, and closing count. A receipt with no reason—or a Zalo message with no receipt—takes time to resolve.

**F7 — Suppliers and accounting tools**

**Hạnh:** I want the restaurant system to show what was ordered, received, rejected and transferred, with the supplier document attached or identified. I already keep supplier payables in MISA accounting software. I don’t need a second payable balance that disagrees with it. For the first release, a reliable export with supplier, document number, date, items, quantity, tax and value would be more useful than a rushed direct connection. We can discuss an integration once both sides’ data are clean.

**F8 — Shared prep costs**

**Hạnh:** At minimum I’d charge each shop for what it actually receives: raw ingredient cost, adjusted for measured preparation yield. Today I allocate too much by branch sales because the transfer notebook is incomplete. I would also like to allocate Thảo’s team’s wages and the back-room overhead, perhaps monthly. I don’t expect Đức to measure labour minutes for every sauce tub during the pilot.

**F9 — Hours and pay**

**Hạnh:** Managers send shift sheets and part-timer hours; some staff sign a paper attendance sheet. I calculate pay in Excel with approved overtime, allowances and deductions. I would leave payroll out of this first project. Better sales software won’t settle our employment policies. An export of who worked a shift could help later, but it should not become the payroll source before we have checked it.

**F10 — Reports**

**Hạnh:** Daily: outlet sales by channel and payment type, cash closing, discounts, voids, refunds, deposits, and exceptions. Weekly: top dishes, purchases, supplier price changes, and expected versus counted stock for selected items. Monthly: branch profit and loss, including delivery fees, food cost, labour, rent and shared-prep allocation. The new system should give us dependable sales and operational stock data directly. I can finish the full P&L in accounting for now, because rent, wages and other expenses won’t all originate in the restaurant system.

**F11 — Controls and access**

**Hạnh:** I want a reason and a named person for a dish removed after it reached the kitchen, a discount, a refund, a changed payment method after settlement, stock adjustments, and changes to a completed cash count. Some need approval too. An issued invoice must follow the proper correction process.

Sales, stock and invoice history must remain retrievable for the legally required period; I would plan for at least ten years of accessible records or exports, then confirm the precise retention treatment with our adviser. Servers shouldn’t see costs or other shops’ sales. Chefs need stock and recipes, not bank receipts. I need all shops’ financial records but no ability to quietly alter a kitchen order.

### Owner

**O1 — Discounts and voids**

**Mai Anh:** I’m comfortable with a manager settling an ordinary complaint up to 5% or VND 150,000 on a bill without calling me. A removed dish after cooking or serving should have a reason and the manager’s name. Anything larger normally comes to me. Reasons could be wrong order, guest changed mind before cooking, kitchen quality issue, long wait, promotion, or staff error—not just “other” every time.

I’d like an approval request on my phone, but I may be driving or in another shop. If I don’t answer within a few minutes, the manager must still deal with the guest. For a genuine service failure, I’d allow a manager to resolve up to VND 300,000 with a second staff member confirming it, then notify me that evening. Hạnh thinks VND 300,000 is too high.

**Hạnh:** I do, unless we can see how often it happens and why.

**O2 — Deposits**

**Mai Anh:** From now on, deposits should go to the company account, with the booking name in the transfer description. I need to stop giving my personal QR when I arrange a party myself. I think a guest cancelling at least 24 hours before should get a full refund. Inside 24 hours, I may need to keep some of it if we bought food specifically for them. Hạnh is right that we must decide exactly what we tell guests; I don’t want staff inventing a different rule at each shop.

**O3 — Menu and prices**

**Mai Anh:** The core menu is shared. There are a few shop-only specials, and the terrace shops sell more grilled sets. I approve menu items and prices; a chef can propose a dish, and a manager can mark it sold out. App prices differ by item and are generally higher, but not by one fixed percentage. For major holidays we publish a dated price list in advance, including which channels it applies to. Today the changes are made separately in each POS and app, and someone can miss one. I don’t want a cashier changing a selling price at the counter.

**O4 — Customers**

**Mai Anh:** We recognise regulars by name or phone and occasionally send a birthday offer manually. I would like one customer list across the shops, then perhaps points or simple vouchers. I don’t want a large loyalty launch in January. We have phone numbers from bookings and bills, but I cannot say every guest agreed to marketing messages. We should ask before sending Zalo or SMS offers, and let them stop them.

**O5 — Access**

**Mai Anh:** Servers should handle their tables and see whether an item is ready, but not cost or overall revenue. Cashiers handle bills, payments and their own shift. Chefs see kitchen orders and the ingredients they manage. Managers run their shop, approve ordinary service fixes and see that shop’s sales and stock. Minh sees purchasing and deliveries across shops; Hạnh sees the financial and invoice picture across shops. I see everything. Managers shouldn’t see another shop’s profit or staff pay.

**O6 — My phone at night**

**Mai Anh:** Before bed I want to see each shop’s sales, cash difference, QR and card totals, app orders, discounts, cancellations, and anything still unresolved. I’d want a prompt alert if cash is out by more than VND 200,000, a served dish is removed for more than VND 100,000, or a manager uses the larger complaint allowance I just described. Don’t wake me for every corrected side dish. An outage lasting more than about 15 minutes during dinner would also matter.

**O7 — Devices and backup**

**Mai Anh:** At Bình Thạnh on Saturday, perhaps four handheld devices for servers and one spare or shared with the manager. I’d let Lan test that number. There is room near the kitchen pass for a screen and power nearby, but Đức wants printed tickets while we test. We have the current printer; more station printers would need a visit to check space and wiring. We don’t have a proper UPS or dedicated backup internet today. Staff have used a phone hotspot, which is not a dependable plan. I prefer shop-owned devices, not everyone’s personal phone.

**O8 — Support and ownership**

**Mai Anh:** We serve until 10:30 p.m., so urgent help must be reachable through dinner and closing, ideally until 11:30. If ordering or billing stops, I want someone to respond within about 15 minutes and help us keep serving; I know a hardware fault might take longer to repair. Lan could own day-to-day use at the pilot shop, Minh would maintain item and supplier information with the chefs, and Hạnh would own payment and invoice checking. I want our company to own and be able to export its data. Hosting in Vietnam would be my preference, though I’d like someone technical to explain the options.

**O9 — Future**

**Mai Anh:** A fourth shop in 2027 is possible, not signed. We’re not planning a franchise. If the three shops grow, Thảo may need a bigger prep location. Table QR ordering and our own ordering website interest me, but I wouldn’t spend January’s pilot money on them. I’ve even imagined a camera that knows every plate leaving the kitchen, but that sounds expensive and isn’t a serious first-stage request.

**O10 — Priorities**

**Mai Anh:** These are my choices today. “Must” means I wouldn’t call that stage successful without it; I’ll adjust them if Lan, Đức or Hạnh shows me a practical problem.

| Capability | Bình Thạnh pilot | Full rollout |
|---|---|---|
| a. Table map and status | Must | Must |
| b. Handheld order to kitchen and bill | Must | Must |
| c. Kitchen tickets/status | Must | Must |
| d. Hold and send later | Should | Must |
| e. Voids, discounts and history | Must | Must |
| f. Split/merge bills and move tables | Must | Must |
| g. Bill-specific QR and automatic confirmation | Could | Should |
| h. Cards, wallets and mixed payments | Must | Must |
| i. Shift counts and handover | Must | Must |
| j. Work through internet loss | Must | Must |
| k. Reservations and deposits | Should | Must |
| l. Waiting list | Could | Should |
| m. Delivery orders in system | Must, entered by staff | Must; connection is a Could |
| n. Sold-out control | Must for floor; app updates can be manual | Must for floor; app updates Should be linked |
| o. Issue e-invoices from this system | Could; keep current provider | Should, through a proven provider connection |
| p. Portions and expected usage | Should for selected items | Must for priority items |
| q. Prep production and transfers | Should | Must |
| r. Purchasing requests through receiving | Could | Should |
| s. Count and variance for first ~15 items | Should | Must |
| t. Same-night phone dashboard | Must | Must |
| u. Payment matching | Should | Must, allowing later app settlement |
| v. Central menu and price management | Should | Must |
| w. Customer list and loyalty | Could: basic list | Should: simple offers/points |
| x. Timekeeping and payroll | Won’t | Won’t |
| y. Guest table QR ordering | Won’t | Won’t |
| z. Accounting export | Should | Must |

**Mai Anh:** For invoices, “Could” in the pilot does **not** mean we stop issuing them. Hạnh’s current process must continue until another one is tested. Likewise, a delivery order must appear in our figures, but I won’t insist that a third-party app connection is ready in January.

### Both

**Z1 — What could hurt the project**

**Hạnh:** Bad starting data. We have different item names between shops and apps, incomplete stock units, and some customer phone numbers repeated. If we migrate all of that without checking it, a polished report will still be wrong. We also need to agree how deposits, delivery fees and shared-prep costs are treated before trusting a profit figure.

**Mai Anh:** Staff adoption. If an order takes too long at 7:30 p.m., they’ll return to paper, whatever we agreed in a meeting. I need Lan and Đức involved in a real dinner-service trial. And I need someone to tell me early if connecting the food apps or bank isn’t commercially possible, because I don’t want a promised feature becoming a surprise just before March.

---

## Session 5 — Requirements confirmation pack (Mai Anh, Hạnh, Lan, Đức; Minh, Thảo, Huy on their parts)

### BA confirmation pack

Participants: Mai Anh, Hạnh, Lan and Đức (Minh, Thảo and Huy may comment on their parts). Below is my summary of what you told me, turned into direction, scope, rules and targets. Please review it like a real client would review a vendor's confirmation pack: for each ID say OK, or give the correction; answer decisions D1-D10; add anything missing; then say whether you would sign off, and with which changes. Answer compactly by ID, in the voice of whoever owns the topic. Around 1200-1800 words.

## A. Goals (from Sessions 1 and 4)
- G1 Same-night view per shop of sales, payments and explained differences (today: next day).
- G2 Accountant's daily matching under 1 hour (today: 2-3 hours).
- G3 Fewer missed/duplicated dishes (today: about 2-3 incidents a week, not logged; we will log from pilot day 1).
- G4 Stock variance of beer and priority meats under 3% (today: estimated 6-8%; baseline to be measured in Oct-Dec).
- G5 Staff do not return to paper: ordering on devices must be as fast as paper at 7:30 p.m.

## B. Solution direction
- Option 1: configure an off-the-shelf product (KiotViet/Sapo/iPOS/CukCuk) and keep Excel for shared prep and payment matching. Cheapest and fastest; leaves your top-3 problems 2 and 3 largely in Excel.
- Option 2: a custom system built around your process, reusing proven services instead of building them (bank QR notifications, your e-invoice provider, export to MISA). Fits the shared prep, controls and matching; highest delivery risk, so it needs a strict phase-1 scope.
- Option 3: hybrid, a product for ordering/billing plus a custom back office for prep and matching. Two systems to keep in sync; depends on the product's data access.
- BA recommendation: Option 2 with the phased scope in C, plus a 2-week technical check (bank QR notification access, printers/offline set-up, MISA formats) before the build commits.

## C. Scope by phase (your O10 ranking, turned into scope)
- C1 Pilot Bình Thạnh (late January), Must: table map and status; handheld ordering straight to kitchen and bill; kitchen tickets per station plus a status view (printed tickets kept); voids/discounts with reasons, limits and history; split/merge/move; cash, card, e-wallet and mixed payments recorded; shift open/close, cash count, handover; keep working offline; delivery orders typed in by staff with the app order number; sold-out control on the floor (app updates manual, with a reminder); same-night owner dashboard.
- C2 Pilot, Should: hold-and-fire; reservations with deposits; recipes and expected usage for the ~15 priority items; shared-prep production and transfers; counts and variance for the ~15 items; payment matching report; central menu and price lists; export to accounting.
- C3 Pilot, Could: bank QR per bill with automatic confirmation; waiting list; basic customer list; purchasing requests to receiving; e-invoice handled as today in MISA meInvoice (no change in pilot).
- C4 Full rollout (end of March), all C1 and C2 items become Must; Should: bank QR per bill, waiting list, linked sold-out updates to apps, e-invoice through a proven provider connection, purchasing requests to receiving, simple offers/points; Could: direct delivery-app connection.
- C5 Won't (this project): timekeeping and payroll; guest table-QR ordering; own ordering website; camera plate tracking; franchise features; supplier payables (they stay in MISA).
- C6 BA concern: G2 depends mostly on bank QR per bill. With QR only as Could in the pilot, the accountant will still match many transfers by hand. I suggest QR-per-bill as Should for the pilot.

## D. Business rules to confirm
- BR1 Manager may discount/comp an ordinary complaint up to the LOWER of 5% of the bill or VND 150,000 per bill, with a reason; above that needs Mai Anh's approval.
- BR2 Reasons are picked from a list: wrong order, guest changed mind before cooking, kitchen quality issue, long wait, promotion, staff error, other (other requires a note and is flagged in reports).
- BR3 A dish not yet started can be removed by the server or cashier with a reason, no approval. A dish started or served needs manager approval plus a reason, and the kitchen must acknowledge the cancellation on its ticket/screen. The chef defines per dish when it counts as started.
- BR4 Staff meals are recorded separately from customer discounts.
- BR5 Deposits go only to the company account with the booking code in the transfer text; a deposit reduces the final bill and is never counted as sales; cancellation 24 hours or more before gives a full refund; later cancellations/no-shows follow a written rule (see D3); unclaimed deposits are never recorded as income automatically.
- BR6 Only Mai Anh approves menu items and selling prices; the accountant sets each item's tax category; a manager or chef can mark items sold out; a cashier can never change a price at the counter. Price lists exist per channel (dine-in/takeaway, GrabFood, ShopeeFood) and dated holiday lists state which channels they cover.
- BR7 A transfer payment counts as paid only when the bank confirms it (never from a screenshot). If automatic confirmation is unavailable, the manager confirms manually with the bank reference, and it is logged.
- BR8 Shift float is VND 1,000,000; handovers are counted and confirmed by both people; a difference is recorded with a reason, never by editing sales.
- BR9 A paid-out (urgent purchase from the drawer) needs a reason and a receipt photo; above a threshold it needs Mai Anh's prior approval (see D4).
- BR10 Issued e-invoices are never deleted in the restaurant system; corrections go through the accountant's provider process.
- BR11 A reason and a named person are required for: removing a dish that reached the kitchen, discounts, refunds, changing a payment method after settlement, stock adjustments, and editing a completed cash count.
- BR12 Receiving cannot be confirmed without counted quantities; the delivered price is compared with the agreed price and differences are flagged; rejected items carry a photo.
- BR13 Shared-prep and inter-shop transfers are recorded by the sender and confirmed by the receiver; differences stay open as "in transit / disputed" until resolved; the receiving shop is charged the raw cost adjusted by measured yield; prep wages and overhead are allocated monthly.
- BR14 An allergy note is structured (allergen picked from a list), printed prominently, and requires chef acknowledgement; the system never labels a dish "allergen-free".
- BR15 Marketing messages only to customers who agreed, with an easy opt-out.
- BR16 Access follows your O5 table (servers: own tables and item status; cashiers: bills and own shift; chefs: kitchen orders, stock and recipes they manage; managers: own shop only; Minh: purchasing across shops; Hạnh: finance across shops; Mai Anh: everything). Detailed sauce recipes are visible only to head chefs and Mai Anh.
- BR17 Immediate alerts to Mai Anh: cash difference above VND 200,000; a served dish removed above VND 100,000; use of the fallback allowance (D2); an outage longer than 15 minutes during service.

## E. Quality targets to confirm
- N1 A trained server adds a dish to an existing table and sends it in at most 4 taps and about 10 seconds; the kitchen ticket prints or appears within 3 seconds.
- N2 With internet down, ordering, kitchen tickets, billing, cash and card recording keep working for at least 4 hours inside the shop; when the internet returns, data synchronises with no duplicate tickets or sales.
- N3 Support reachable 10:00-23:30 daily, first response within 15 minutes when ordering or billing stops.
- N4 Sales, invoice and stock records kept at least 10 years (to be confirmed by your adviser); daily backups; your company owns the data and can export it at any time.
- N5 Hosting in Vietnam; personal data handled under the Personal Data Protection Law and Decree 356/2025.
- N6 Sized for 4 shops (a possible 4th shop in 2027), about 20 devices per shop.
- N7 Shop-owned devices only; each person logs in with a personal PIN; everything sensitive is in an audit log that cannot be edited.
- N8 Monthly running cost (hosting, support, third-party fees) within VND 8-12 million; hardware quoted separately.

## F. Decisions needed from you
- D1 Is BR1 per bill only, or is there also a per-shift cap for a manager?
- D2 Fallback allowance when Mai Anh does not answer: accept up to VND 300,000 with a second staff confirmation after how many minutes? Hạnh disagrees; what is the final rule, and who may be the second person?
- D3 Deposit rule for cancellations under 24 hours and no-shows (keep all, a percentage, or only food bought for the booking?).
- D4 Paid-out amount above which prior approval is required.
- D5 Solution direction: Option 2 with the 2-week technical check, or do you want the off-the-shelf fit test first?
- D6 Pilot QR-per-bill: Should (my suggestion) or Could?
- D7 Hardware: are you willing to budget a UPS and a 4G backup router per shop? Without them, N2 relies on the shop's power staying on.
- D8 Kitchen at Bình Thạnh: station printers (grill, wok/clay pot, hotpot) plus one status screen at the pass. OK to arrange a site visit?
- D9 Master-data clean-up owners: menu names and units (Minh with chefs), tax categories (Hạnh), tables and floor plan (Lan). Deadline end of December?
- D10 Baseline measurement from October to December (missed-dish log, stock variance for the 15 items, matching time). Who records it?

## G. Assumptions (tell me if any is wrong)
- A1 E-invoices keep being issued in MISA meInvoice during the pilot; the new system hands over invoice data rather than replacing it.
- A2 Delivery-app connections and bank notification access depend on commercial approval from Grab, Shopee and the bank; we do not promise them until confirmed.
- A3 Payroll and supplier payables stay outside this system; we only export the data you need.
- A4 A real Friday/Saturday dinner trial with Lan and Đức happens before the pilot go-live.

### Client review

**Mai Anh:** This is close to what we discussed, but I would change several rules and phase promises before signing it. Hạnh, Lan and Đức have added their views below.

### A. Goals

- **G1 — OK.** **Mai Anh:** “Same night” means a useful view of each shop after closing, with unresolved amounts shown honestly. I don’t expect a GrabFood settlement that arrives days later to be confirmed that night.
- **G2 — Correction.** **Hạnh:** Under one hour is a target **after the full rollout**, not a condition for the January pilot. Please measure my hands-on matching time; merely moving an unexplained difference to tomorrow does not count as saving time.
- **G3 — OK.** **Lan:** Log missed *and* duplicated dishes, including those we catch before a guest complains.
- **G4 — OK, with a qualification.** **Đức:** Compare like units and account for measured trimming and recorded waste. I will dispute a percentage based on bad opening counts.
- **G5 — Correction.** **Lan:** Staff may use paper during a genuine outage or emergency. The goal is that they choose the devices during a normal Friday rush.

### B. Direction

**Mai Anh:** I favour **Option 2**, subject to the two-week technical and price check. Please also demonstrate whether an existing product could cover the front-of-house work during those two weeks. I won’t pay to rebuild ordinary ordering if an existing component fits and can share dependable data. I want the decision supported by a costed plan within our budget, not just a statement that custom is flexible.

### C. Scope

- **C1 — Mostly OK.** **Đức:** Keep printed tickets in the pilot, with the status screen as an aid. **Lan:** Delivery orders must carry the app order number so Huy can find a duplicate entry.
- **C2 — OK as priorities, but don’t promise that every “Should” will be finished by January.** **Hạnh:** For stock, start with agreed units and a count we trust. A misleading expected-usage report would be worse than waiting another few weeks.
- **C3 — Correction.** **Hạnh:** Existing meInvoice handling is a continuing business process, not a “Could” feature. The pilot must give me dependable sale and buyer details to issue and reconcile invoices, even if I transfer them manually.
- **C4 — Correction.** **Mai Anh:** Do not turn *all* of C2 into Must automatically. Hold-and-fire, deposits, priority-item counts and prep transfers matter for March. A waiting list, linked app availability, purchasing workflow and loyalty remain Should. Direct app connections remain Could until access and cost are confirmed.
- **C5 — OK.** **Mai Anh:** That expensive camera idea is definitely out. Payroll and supplier balances stay in Hạnh’s tools.
- **C6 — OK.** **Hạnh:** Make bill-specific QR a pilot Should, subject to bank access. If confirmation cannot be automated by January, I still want a bill reference and a clear manual matching route. Do not claim G2 has been met merely because a QR is printed.

### D. Business rules

- **BR1 — Correction.** **Mai Anh:** The lower of 5% and VND 150,000 is too restrictive for a small bill. My normal manager limit is **up to 10% of the bill, capped at VND 150,000**, with a reason. Removing an incorrectly charged dish is not a “discount” against this allowance.
- **BR2 — OK.** **Lan:** Add “duplicate entry” as a reason. “Other” should need a short note.
- **BR3 — Correction.** **Đức:** A server can correct a mistake **before** sending it to my kitchen. Once a ticket has reached us, even if we haven’t started cooking, the change must reach the kitchen and be acknowledged; the manager should approve its removal. “Started” cannot be one fixed moment for every dish. **Lan:** For a quality complaint after serving, I need to settle the guest under my allowance, then record what happened.
- **BR4 — OK.** **Hạnh:** Staff meals should be identified separately, even if made from ingredients rather than a menu dish.
- **BR5 — OK once D3 is added.** **Hạnh:** Show a deposit as money held against a named booking, then applied to the final amount due. Keep its receipt and any refund visible.
- **BR6 — OK.** **Mai Anh:** A manager and chef may both report a sell-out; the person updating availability must tell the other channels. **Hạnh:** I confirm the tax category before a new item is sold.
- **BR7 — Correction.** **Huy:** I normally check our banking app at the cashier desk. I should be allowed to confirm an actual bank credit with amount and reference; Lan should approve an uncertain match or later override. A screenshot alone never settles it.
- **BR8 — OK.** **Hạnh:** The VND 1 million is the change float, not permission to spend it without recording a paid-out.
- **BR9 — OK, with D4.** **Lan:** Sometimes a supplier gives a paper receipt that we photograph after service. I can record the purchase immediately and attach the photo before closing.
- **BR10 and BR11 — OK.** **Hạnh:** Keep the original event and the correction. I need to know who approved it as well as who entered it.
- **BR12 — Correction.** **Minh:** Counted quantity is essential. A photo is especially useful for rejected or damaged goods, but don’t block us from recording a short delivery because a camera is unavailable. Record the dispute and attach evidence afterward.
- **BR13 — Mostly OK.** **Thảo:** We must be able to record a leaking or spoiled tub, not just a quantity dispute. **Hạnh:** Don’t charge the receiving shop for a transfer it disputes or never receives; hold that cost for review. Ingredient cost and measured yield come first, with wages and overhead allocated monthly.
- **BR14 — Correction.** **Đức:** A pick-list can help, but it cannot cover every allergy. Keep the guest’s exact words too, and require the server to speak to me. My acknowledgement means I have reviewed the request; it does **not** certify that the food is allergen-free.
- **BR15 and BR16 — OK.** **Mai Anh:** I want consent recorded before marketing. **Đức:** Detailed sauce recipes should be restricted to head chefs and Mai Anh, as written.
- **BR17 — OK.** **Mai Anh:** Send immediate alerts for those exceptions, but put routine corrections in my nightly view.

### E. Quality targets

- **N1 — Trial target, not yet an agreed acceptance limit.** **Lan:** Four taps and ten seconds sounds good, but test it with our servers using real additions and special instructions on a busy night. **Đức:** The kitchen must get the *correct* ticket; speed alone won’t impress me.
- **N2 — Correction.** **Hạnh:** We can record card as the intended payment method offline, but the system must not claim that an unapproved card or bank transfer is paid. **Lan:** We do need local ordering, printing and cash billing through an internet outage, followed by reconciliation without duplicate sales or tickets. Four hours is a sensible target if power remains available.
- **N3 — OK.** **Mai Anh:** Fifteen minutes is a first human response for an ordering or billing stoppage, with a practical way to continue serving. I understand repair may take longer.
- **N4 — OK subject to adviser confirmation.** **Hạnh:** I need usable exports and a way to retrieve older transactions, not merely a promise that backups exist.
- **N5 — Correction.** **Mai Anh:** Hosting in Vietnam is my **preference** and should be priced. Proper personal-data handling is mandatory. I don’t want to reject a workable option solely because its hosting location differs before we understand the implications.
- **N6 — Correction.** **Mai Anh:** Allow for a possible fourth shop, but don’t charge us now for 20 active devices at every shop. Lan estimates about five floor handhelds at Bình Thạnh plus the cashier and kitchen equipment.
- **N7 — Mostly OK.** **Lan:** Devices can be shared across shifts, but staff must use their own login or PIN. We shouldn’t rely on personal phones for normal service.
- **N8 — Correction.** **Mai Anh:** VND 8–12 million a month is my preferred range for **fixed software hosting and support across three shops**. Show hardware, invoice usage, SMS, payment charges and app commissions separately. I need to approve their likely totals too.

### F. Decisions

- **D1 — Per bill and per shift.** **Mai Anh:** Use the revised BR1 limit per bill. If one manager’s ordinary complaint discounts reach **VND 600,000 in a shift**, further ones come to me, except the fallback in D2. I want to review whether this limit is sensible after the pilot.
- **D2 — Three minutes.** **Mai Anh:** If I have not answered after three minutes and a guest is waiting, Lan may resolve a genuine service failure up to **VND 300,000** with Huy or the head chef confirming what occurred. It is logged and alerts me immediately. It is for an exceptional complaint, not a second general discount allowance. **Hạnh:** I’ll accept that for the pilot if we review every case.
- **D3 — Communicated terms only.** **Mai Anh:** Cancel 24 hours or more ahead: full refund. Under 24 hours or a no-show: retain **50% of the deposit**, refund the rest, unless we agree to move the booking within seven days. If we cannot show that the guest was told these terms when booking, refund the full deposit. **Hạnh:** Please have me check the wording and accounting treatment before it goes into use.
- **D4 — VND 300,000.** **Mai Anh:** A manager may make an urgent paid-out up to that amount with a reason and receipt. Above it, call me first, except an immediate safety or service emergency; then call as soon as practical and document it.
- **D5 — Option 2, conditionally**, with the two-week technical check and a front-of-house product fit test as described above.
- **D6 — Should for the pilot**, conditional on bank access. It is not permission to mark unconfirmed transfers paid.
- **D7 — Yes, quote UPS and dedicated 4G backup for each shop.** **Mai Anh:** We may buy for the pilot first, then the other two before rollout.
- **D8 — Yes to a site visit.** **Đức:** Check the actual heat, grease, wiring and ticket location with me before deciding on three printers and a screen. I have not agreed to that exact hardware count.
- **D9 — Split the deadline.** **Mai Anh:** Lan’s floor plan and the pilot menu and units should be checked by **mid-December**. Hạnh can verify pilot tax categories then. Clean the other two shops’ differences by the end of January, rather than pretending all data will be ready together.
- **D10 — Agreed.** **Lan:** I’ll keep the missed/duplicate dish log, including the cause when we know it. **Minh and Thảo:** We’ll organise counts and movement records for the priority stock items with the chefs. **Hạnh:** I’ll time my daily matching and report the baseline each month. We can start in October; December figures may be better than October’s.

### G. Assumptions

- **A1 — OK with C3’s correction.** **Hạnh:** meInvoice remains the issuing tool during the pilot; invoice data must reach me reliably.
- **A2 — OK.** **Mai Anh:** Tell me early if approval, access or charges make an integration impractical.
- **A3 — OK.** **Hạnh:** An accounting export must have enough detail for me to check it before importing.
- **A4 — OK.** **Lan and Đức:** Include a genuinely busy dinner, not only an afternoon demonstration.

### Sign-off

**Mai Anh:** I would **sign off on the direction and revised priorities**, but not this pack as written. Please incorporate the corrections to **BR1, BR3, N2, N8, C4 and D1–D10**, and show the costed outcome of the two-week check before I authorise the build. One more thing that could hurt us: assign time for training part-time staff and for recovering from a failed Friday-night device or printer. Those people and that fallback are easy to overlook when everyone in the meeting already knows the restaurant.

---

## Session 6 — Revised baseline confirmation (email)

### BA revised items

Thank you. I have incorporated your corrections. Please confirm or correct only these points, briefly, by ID (around 300-600 words in total).

R1 (BR1 + D1) A manager may discount or comp an ordinary complaint up to 10% of the bill, capped at VND 150,000 per bill, with a reason. Correcting a wrongly charged item does not count against this allowance. Per-shift cap: VND 600,000 per manager; above that, Mai Anh approves. Reviewed after the pilot.
R2 (BR3) Before an order is sent to the kitchen, the server can correct it freely. After a ticket has reached the kitchen (started or not), removing or changing it needs manager approval and a reason, and the kitchen must acknowledge the change on its ticket/screen. A quality complaint after serving is settled by the manager under R1, then recorded.
R3 (D2) If Mai Anh has not answered within 3 minutes, the manager may resolve a genuine service failure up to VND 300,000 with the cashier or head chef confirming what happened. It alerts Mai Anh immediately and Hạnh reviews every case during the pilot.
R4 (D3) Cancellation 24 hours or more ahead: full refund. Under 24 hours or no-show: keep 50% and refund the rest, unless the booking is moved within 7 days. If we cannot show the guest received these terms (the booking confirmation message is stored), refund in full. Hạnh approves the wording and accounting treatment before use.
R5 (D4) Paid-outs up to VND 300,000 with a reason and a receipt (photo may be attached before closing). Above that, call Mai Anh first, except a safety or service emergency, which is documented afterwards.
R6 (N2) During an internet outage, ordering, kitchen printing and cash billing continue inside the shop for at least 4 hours if power is available. Card or QR can be recorded as "intended, unconfirmed" and is only marked paid after confirmation. No duplicate tickets or sales after recovery. UPS and 4G backup quoted per shop.
R7 (N8) VND 8-12 million per month covers fixed hosting and support for three shops. Hardware, e-invoice usage, SMS/Zalo messages, payment fees and app commissions are listed separately for your approval.
R8 Rollout priorities: your Session 4 table ranked recipes/expected usage for priority items (p), payment matching (u), central menu and price management (v) and accounting export (z) as Must for March. In Session 5 you named hold-and-fire, deposits, priority-item counts and prep transfers as the March essentials. Do p, u, v and z stay Must for March, or move to Should?
R9 New transition requirements from your sign-off note: (a) a training plan that includes part-time staff, short role-based guides, and a supervised busy-night trial; (b) a failure playbook: spare handheld, backup printing route, numbered paper fallback slips, and a re-entry check after recovery so nothing is entered twice. Anything to add?
R10 From Session 3: when receiving chilled meat and seafood, should the temperature be recorded?

(Meta note for the simulation: after this reply I will treat the requirements baseline as signed off, pending the two-week technical check, and I will then open your persona file for my elicitation audit.)

### Client confirmation

**R1 — Confirmed. Mai Anh:** The VND 600,000 shift cap applies to each manager’s ordinary complaint allowances. We’ll review actual use after the pilot.

**R2 — Confirmed. Lan:** Please make sure the kitchen acknowledgement is visible to me. I don’t want to assume a cancelled dish has stopped cooking. **Đức:** Agreed; an acknowledgement means we saw the change, not necessarily that the ingredients can still be saved.

**R3 — Confirmed. Mai Anh:** This is an exception for a genuine service failure, not another routine discount limit. **Hạnh:** I’ll review each use during the pilot.

**R4 — Confirmed, subject to Hạnh’s review before we tell guests. Hạnh:** Keep the booking confirmation and the record of whether the deposit was refunded, retained or applied to a moved booking.

**R5 — Confirmed. Mai Anh:** The emergency exception must include why prior approval was impractical.

**R6 — Confirmed. Lan:** Test recovery during the supervised trial, including orders already printed just before the connection fails. **Huy:** “Intended, unconfirmed” must remain visible at closing so nobody mistakes it for money received.

**R7 — Confirmed. Mai Anh:** I want the separate charges estimated for our expected usage, not only listed by name, before approving the cost.

**R8 — They stay Must for March. Mai Anh:** For **p**, that means agreed portions and expected usage for the first priority items, not every recipe. For **u**, Hạnh must be able to match cash, bank and card and account for later app settlements, even if importing an app statement is manual. For **v**, one controlled menu with shop, channel and dated holiday prices. For **z**, an export Hạnh has tested with MISA. Hold-and-fire, deposits, priority counts and prep transfers also remain March essentials.

**R9 — Confirmed. Lan:** Include part-timers who only work weekends; an afternoon lesson alone won’t prepare them. **Huy:** Assign one person to check paper slips against entered orders after recovery, and have the manager sign off that check before closing.

**R10 — Yes. Đức:** The receiver should record the measured temperature for chilled meat and seafood at delivery, along with the time and any quality concern. Minh and I need to agree the acceptable ranges and what to do with a questionable delivery before this becomes a receiving rule; I won’t invent one in this email.

**Mai Anh:** With those clarifications, I confirm the requirements baseline. Authorisation to build still depends on the costed two-week technical check we discussed.

---

## Session 7 — Planning gaps: Tết timing, other managers, rounding (Mai Anh, Hạnh)

### BA questions

Q1 Lunar New Year 2027 falls around 6 February, and late January is usually the year-end party ("tat nien") peak for restaurants. The pilot go-live is planned for late January. How busy are those weeks for you, do you close for Tet (which days), and do you publish a Tet price list? Would you rather go live in the first half of January (before the party peak) or after Tet (around 22 February), keeping the other two shops by end of March?
Q2 I have not interviewed the Phú Nhuận and District 3 managers. Phú Nhuận has the unstable internet and District 3 has the private room and terrace. Can I have 30 minutes with each of them before the rollout design is finalised (target: before the end of October)?
Q3 Money rounding: are all menu prices whole thousands of VND? When a 10% discount or a split bill produces odd amounts, should the system round to the nearest VND 1,000, and who absorbs the difference?

### Client answers

**Q1 — Mai Anh:** You’re right about the timing. Year-end parties make the second half of January through the days before Tết some of our busiest evenings. We usually publish party sets and a dated holiday price list in January; Lan can give you last year’s exact dates. We expect to close **5–9 February**, reopen with a shorter menu on the 10th, and return to normal service as staff come back. Those dates still need confirming with the managers.

I would choose a **Bình Thạnh go-live around 22 February**, after the team has settled back in. We can rehearse in January without putting an unproven process into the party peak. End-of-March rollout remains my goal for the other two shops, but I won’t force it if the busy-night pilot exposes serious ordering or payment problems.

**Q2 — Mai Anh:** Yes. I’ll arrange 30 minutes each with Thanh, the Phú Nhuận manager, and Quyên, the District 3 manager, before the end of October. Please ask Thanh to walk through an actual internet outage and recovery. Quyên can show you how private-room bookings, terrace moves and larger parties work there. Lan’s floor plan shouldn’t be copied blindly to either shop.

**Q3 — Hạnh:** Menu prices are generally whole thousands of đồng, but percentage discounts and split payments can produce exact amounts that aren’t. **Do not round every item or every person’s share to the nearest VND 1,000**; the shares must still add up to the bill. Show the exact amount for bank transfer or card.

**Mai Anh:** For **cash only**, I’d round the final amount the guest owes **down** to the nearest VND 1,000, so the restaurant absorbs at most VND 999. **Hạnh:** Record that small reduction explicitly before the final invoice is issued. Don’t hide it as a cash-drawer difference.

---

## Session 8 — Phú Nhuận and District 3 managers (Thanh, Quyên)

### BA questions

Participants: Thanh (Phú Nhuận manager) and Quyên (District 3 manager). Please answer in their voices, label the speaker, and use concrete recent examples and rough numbers. Around 900-1400 words in total.

Thanh (Phú Nhuận)
T1. Walk me through the August outage: when it started, what staff did, how orders reached the kitchen, how bills were settled, what happened when the connection came back, how the duplicate kitchen tickets arose, and how long the clean-up took.
T2. How often and for how long does the connection drop now? Is it the internet line, the Wi-Fi inside the shop, or power? Where are the router, POS and printer physically located?
T3. What is different at Phú Nhuận compared with Bình Thạnh in service: the screened-off group section, the front tables, staffing, the delivery share?
T4. Is there anything you need that Bình Thạnh does not?

Quyên (District 3)
Q1. Private room: how are bookings handled (minimum spend, set menus, deposits, time slots, decorations or extra fees)? What goes wrong?
Q2. Terrace: how do weather moves work for whole groups mid-meal, and what goes wrong?
Q3. Large parties (for example 20-40 people): pre-ordered set menus, timing, paying across several companies, VAT invoices, deposits.
Q4. Is there anything specific to District 3 that Bình Thạnh does not have?

Both
B1. What do you want to see for your own shop at night and during service?
B2. What worries you most about going live in March?

### Client answers

**Thanh (Phú Nhuận manager):** I’m Vũ Minh Thanh. Our shop has 20 tables, including four near the open front. We can screen off part of the indoor area for a group, but it is still part of the main restaurant.

### Phú Nhuận

**T1 — The August outage**

**Thanh:** It was a Saturday in late August, just after 7 p.m. The cashier’s POS stopped connecting while several tables were ordering. The lights and kitchen printer still had power. I told servers to number their paper slips and take them directly to the kitchen. The chef put those slips on the ticket rail. We wrote additions on new slips with the table number and “addition” across the top.

For bills, the cashier totalled the slips by hand. Two groups paid cash. One paid by card using the separate terminal. A transfer customer waited while we checked the bank on a phone using mobile data; we did not accept only their screenshot.

The connection returned after roughly half an hour. We then made the mistake: the cashier entered the paper orders into POS365 to complete the records, but the kitchen printer produced tickets for some of those entries. We had marked the *paper* slips as served, but not clearly marked which ones should be entered without being cooked again. Two dishes were started twice before I stopped the cooks. We used one; the other was wasted. I stayed with the cashier about an hour after closing comparing slips, bills and POS entries, and Hạnh asked us questions the next morning. I cannot give you an exact loss figure because we didn’t record the wasted plate properly.

**T2 — Connection and equipment**

**Thanh:** We still see a drop roughly twice a month, usually 15–40 minutes. I think most have been the internet line rather than power: phones remain connected to the shop’s Wi-Fi but pages stop loading, while the lights stay on. Once, the front tables had weak Wi-Fi even though the cashier’s connection worked, so that may be a separate coverage problem. I’m not qualified to diagnose the router.

The router and cashier POS are behind the front counter. The kitchen printer is near the kitchen pass, connected through the shop network. The router’s location is awkward to reach when the counter is crowded. We have used a manager’s phone hotspot, but then that phone has to stay at the counter. There is no dedicated 4G router or UPS yet.

**T3 — Service differences**

**Thanh:** We have about 80 seats, fewer than Bình Thạnh. The screened-off section uses four normal tables; if one group takes it, I lose flexibility to seat small walk-ins there. The front tables are popular on cooler evenings, but guests may ask to move inside when it rains or gets hot. We run with roughly the same roles as Bình Thạnh, though I usually have one fewer server on a quiet weekday.

Delivery feels a little bigger here—perhaps a fifth to a quarter of our sales on some weekdays—but Hạnh should check the actual number. We have couriers waiting close to the entrance, where walk-in groups also arrive. A server sometimes has to choose between greeting guests and answering an app tablet.

**T4 — What we need specifically**

**Thanh:** The outage recovery matters more to me than an attractive table map. I need to know which paper orders have already reached the kitchen and which have only been entered later for the bill. I also need the screened-off four tables shown as separate tables that can be reserved together; calling it one permanent “room” would make ordinary weekdays harder.

### District 3

**Quyên (District 3 manager):** I’m Đỗ Ngọc Quyên. We have 22 tables, about 88 seats. Three tables are in the private room and four are on the terrace; those are included in the 22.

**Q1 — Private room**

**Quyên:** Most bookings come by phone, Zalo or Facebook and go into our booking notebook. For the private room, we usually ask for a minimum spend of about VND 3 million at lunch or VND 5 million on a busy weekend evening, rather than charging rent for the room. I confirm the figure with the guest because menus and group sizes vary. Groups of eight or more commonly leave a VND 500,000–2 million deposit. If they choose a set menu, I send the menu image and write their choices in the notebook.

We normally offer a lunch booking or an evening booking, not a strict two-hour slot. A family can bring small decorations if they arrange it with us; we don’t have a standard decoration fee. The trouble comes when “private room for 12” becomes 17 guests, or the host tells one staff member about a changed set but the kitchen still has the first list. Last weekend a birthday group added five people that afternoon. I could seat them, but the planned dishes and deposit note were in different Zalo messages.

**Q2 — Terrace moves**

**Quyên:** If rain looks likely, I try not to promise an indoor backup table we don’t have. When a whole group moves mid-meal, servers must carry the food, drinks and paper slips, and tell the cashier the new table numbers. We usually move them to several indoor tables rather than one neat replacement. Last month a group of ten left two terrace tables for three indoor tables. Their first bill was still under a terrace table, while a later beer order was entered under an indoor table. We found it before payment, but the cashier had to assemble the bill by hand.

**Q3 — Large parties**

**Quyên:** A 20–40-person party may take the private room plus nearby tables, or a larger section of the restaurant. They often choose two or three set menus in advance and specify when to bring out the grill dishes and hotpots. I call the head chef the day before to check quantities. The deposit comes off the final bill, but the cashier has to find my notebook entry to know the amount.

Payment is not always one transaction. One company may pay for food and request one invoice while guests pay for extra beer themselves. At other events, two departments ask for separate invoices with different company details. I cannot decide the correct invoice treatment at the table; I ask Hạnh. We need those instructions before the party arrives. A last-minute request to divide shared set menus between companies is particularly difficult.

**Q4 — What is specific here**

**Quyên:** We need to show that one event occupies several tables while those tables remain visible individually. We also need room minimum-spend and pre-order notes where the manager and cashier can find them. I would not assume every private-room booking owes the same deposit or uses the same set menu.

### Both managers

**B1 — During service and at night**

**Thanh:** During service, I want to see open tables, orders that haven’t reached the kitchen, dishes waiting too long, sold-out items and any payment still unconfirmed. At night I want my own shop’s sales by payment method, counted versus expected cash, and a short list of unresolved orders or transfers. I do not need the other shops’ profit figures.

**Quyên:** I want the same, plus upcoming parties, their deposits, pre-ordered dishes and which tables they occupy. Before closing I want to know whether every moved table and extra party order ended up on the correct bill.

**B2 — March rollout concern**

**Thanh:** My worry is that our first real outage happens after the trainers leave. I want my cashier, servers and chef to practise the paper fallback and recovery together, not just watch someone demonstrate it.

**Quyên:** My worry is copying Bình Thạnh’s setup and discovering during a large party that it cannot represent our room, terrace move or split company payment. Please test one of our actual group-booking examples with us before calling District 3 ready.

---

## Change request CR-01 — Field research (Codex, out of character) and change request from Mai Anh

### BA request

# PHASE 1 - FIELD RESEARCH (use live web search; Vietnam focus; 2025-2026)

Research how real restaurants in Vietnam actually run the following, with sources:

1. **QR table self-ordering** ("goi mon qua QR tai ban"): which vendors offer it (iPOS/FABi O2O, KiotViet, Sapo FnB, MISA CukCuk, POS365, Ocha, others) and how:
   - do guest orders go straight to the kitchen, or wait for staff confirmation (first order only, every order, above a value)?
   - how are table QR codes protected against fake or remote orders (static vs. rotating QR, table session opened by staff, PIN/code, location checks)?
   - group ordering from several phones at one table; set menus / hotpot / BBQ with options; allergies and special notes;
   - typical adoption and pitfalls (older guests, weak signal, menu photos/content effort, upselling), effect on staffing and service style.
2. **Real-time dish status shown to guests**: how common it is, which statuses are shown, and how much it depends on cooks actually using a kitchen display (bumping items).
3. **Self-checkout by automatic bank transfer**:
   - dynamic VietQR with exact amount and a reference code;
   - how confirmation is automated: bank Open API (check Circular 64/2024/TT-NHNN and its timeline), intermediaries such as payOS, SePay, Casso, and payment gateways (VNPAY, MoMo, ZaloPay, OnePay): latency, pricing model, bank coverage, reliability, what the merchant must sign up for;
   - how a guest pays from the same phone that displays the QR (banking-app deeplinks, saving the QR image, e-wallet redirects), and what fails most often.
4. **Legal/compliance** for self-ordering and self-payment: e-invoice issuance at self-checkout (current decree context: Decree 254/2026), rules on non-cash payment (e.g., Decree 52/2024), personal data when guests enter phone or company details (Personal Data Protection Law + Decree 356/2025), selling alcohol to people under 18 (Law on Prevention and Control of Harmful Effects of Alcoholic Beverages 2019).
5. **Failure modes and controls**: duplicate payment, wrong amount, delayed bank notification, guest leaves before confirmation, refunds, prank orders, orders during an internet outage.

Prefer primary/authoritative sources (government portals, official vendor or bank pages, reputable news). Mark anything unverifiable as UNVERIFIED. Do not invent citations.

# PHASE 2 - BACK IN CHARACTER (Mai Anh)

Mai Anh has now decided she wants:
(a) guests to order by scanning a QR code at the table,
(b) the kitchen's dish status shown to guests in real time on their phones,
(c) guests to be able to pay by themselves by bank transfer with automatic confirmation.

Write her change-request message to the BA: why she changed her mind, what she expects, what worries her, and how she sees it against the pilot timeline and budget. Keep it informal, like a real owner; ground it in the research; stay consistent with everything said so far (including her earlier statement that she did not want to spend January pilot money on table QR ordering, the confirmed baseline, the Tet timing and the ~22 February pilot). Do not write requirements for me.

# OUTPUT (stdout), exactly this structure

## A. Field research brief
Structured by the 5 topics, bullet points, sources inline. Roughly 1000-1400 words.

## B. Change request from Mai Anh
In character. Short and informal.

### Codex output

## A. Field research brief

*Research snapshot: September 2026. Vendor documentation establishes available features, but it does not establish how widely restaurants use them. Items marked **UNVERIFIED** need a vendor demonstration or local trial.*

**1. QR table self-ordering**

- iPOS FABi O2O, KiotViet, Sapo FnB, MISA CukCuk and POS365 all document QR ordering at tables. Their order paths differ. [iPOS’s pay-later flow](https://fabi-docs.ipos.vn/) sends an order to a staff handheld for confirmation, though its documentation also describes an automatic-confirmation setting. [KiotViet](https://www.kiotviet.vn/huong-dan-su-dung-kiotviet/fnb-thuc-don-dien-tu/goi-mon-qua-ma-qr/) offers cashier approval or automatic acceptance and kitchen printing when its conditions are met. [Sapo’s Web Order](https://www.sapo.vn/fnb-ban-hang-online-web-order.html) says staff confirm before informing the kitchen. [POS365](https://www.pos365.vn/tich-hop-he-thong-kds-9006.html) describes guest QR orders reaching its kitchen display, but the approval setting for every order is **UNVERIFIED**.
- MISA’s documentation deserves a live check: its [general table-order guide, updated 23 September 2026](https://helpv2.cukcuk.vn/vi/kb/thiet-lap-goi-mon-tai-ban), says the cashier confirms QR orders to guard against orders from people outside the restaurant; a [buffet-set guide updated 9 September](https://helpv2.cukcuk.vn/vi/kb/quan-ly-co-the-thiet-lap-duoc-thuc-don-dang-cha-con-cho-cac-set-buffet-va-ap-dung-duoc-tren-trang-goi-mon-tai-ban) describes an automatic-confirmation option. I could verify [Ocha’s POS site](https://ocha.vn/), but not a current table-order flow or its controls: **UNVERIFIED**.
- Downloadable QR codes assigned to individual tables are documented by [KiotViet](https://www.kiotviet.vn/huong-dan-su-dung-kiotviet/fnb-thiet-lap/phong-ban/) and [iPOS](https://ipos.vn/wp-content/uploads/2020/10/Tai-lieu-van-hanh_O2O_Web_V2.1.pdf). A photographed or shared static code could therefore be used away from the table unless another check intervenes—an **inference**, not a reported incident rate. KiotViet offers an optional phone-location radius check; MISA’s general flow uses staff confirmation. I found no verified across-vendor evidence for rotating codes, staff-opened sessions or table PINs. [KiotViet QR guide](https://www.kiotviet.vn/huong-dan-su-dung-kiotviet/fnb-thuc-don-dien-tu/goi-mon-qua-ma-qr/), [MISA guide](https://helpv2.cukcuk.vn/vi/kb/thiet-lap-goi-mon-tai-ban)
- Several phones can contribute orders to one table: [KiotViet](https://www.kiotviet.vn/huong-dan-su-dung-kiotviet/fnb-thuc-don-dien-tu/goi-mon-qua-ma-qr/) documents merging near-simultaneous QR requests and retaining combo components, options, toppings and notes. [MISA](https://helpv2.cukcuk.vn/vi/kb/quan-ly-co-the-thiet-lap-duoc-thuc-don-dang-cha-con-cho-cac-set-buffet-va-ap-dung-duoc-tren-trang-goi-mon-tai-ban) documents selectable buffet-set components. Whether a vendor handles *this restaurant’s* hotpot timing, allergy conversation and split-bill cases well is **UNVERIFIED**. An [iPOS restaurant case study](https://ipos.vn/kui-buffet-hotpot/) reports initial guest reluctance to replace staff ordering. National adoption rates, staff savings and upselling effects are **UNVERIFIED**; photos, descriptions, Wi-Fi and a staff-assisted path all affect the experience.

**2. Dish status on the guest’s phone**

- Guest-facing status exists, but “real time” can mean different things. [KiotViet](https://www.kiotviet.vn/huong-dan-su-dung-kiotviet/fnb-thuc-don-dien-tu/goi-mon-qua-ma-qr/) shows **not yet served, served, or cancelled**; its guide says an employee manually marks a dish served. [POS365](https://www.pos365.vn/khach-hang-tu-goi-mon-qua-qr-9005.html) advertises guest status tracking alongside kitchen-display integration. I found no reliable Vietnam-wide measure of how common guest status is: **UNVERIFIED**.
- A guest view can only be as current as the staff actions feeding it. If cooks do not advance items on a kitchen display, or runners do not mark food delivered, “preparing” may remain on the phone after a plate reaches the table. That operational dependency is an **inference** from the documented status actions, not proof that a given vendor is inaccurate. [KiotViet status instructions](https://www.kiotviet.vn/huong-dan-su-dung-kiotviet/fnb-thuc-don-dien-tu/goi-mon-qua-ma-qr/)

**3. Self-payment by bank transfer**

- A bill-specific VietQR can prefill a bank account, **exact amount and transfer description/reference**; generating the image alone does not confirm payment. [VietQR’s API guide](https://www.vietqr.io/intro/) documents the amount and `addInfo` fields and distinguishes QR generation from payOS confirmation. Confirmation requires a bank or authorised service to report the credit and the restaurant to match it to the bill.
- [Circular 64/2024/TT-NHNN](https://vbpl.vn/nganhangnhanuoc/Pages/vbpq-toanvan.aspx?ItemID=174547), effective 1 March 2025, sets a framework for banking Open APIs; it does **not** itself grant every restaurant access to a real-time Vietcombank transaction feed. The bank’s product, contract and access terms still matter. [payOS](https://payos.vn/docs/api/) offers payment links and signed webhooks after merchant verification and channel setup. [SePay](https://developer.sepay.vn/vi) and [Casso](https://developer.casso.vn/) document bank-transaction webhooks; Casso distinguishes transaction feeds from its payOS payment-confirmation product.
- Pricing and bank coverage depend on the selected account and contract. [Casso](https://casso.vn/bang-gia/) publishes tiers based on transactions and connected banks; [payOS](https://payos.vn/3-phut-hieu-ro-ve-payos/) describes free starter quotas followed by paid arrangements. [SePay](https://sepay.vn/bang-gia.html) publishes its own plans. Gateways such as [VNPAY](https://mobile.vnpay.vn/), [MoMo](https://developers.momo.vn/v3/docs/payment/api/result-handling/notification/), [ZaloPay](https://docs.zalopay.vn/vi/docs/guides/payment-acceptance/zalopay-emvco-qr/intro/) and [OnePay](https://onepay.vn/documents/2024.11_OnePay_Huong_dan_thanh_toan.pdf) provide different merchant checkout or notification arrangements; they should not be treated as interchangeable with a plain bank transfer. SePay claims roughly **5–10 seconds** behind internet banking, but an independent cross-bank latency guarantee is **UNVERIFIED**. [SePay explanation](https://docs.sepay.vn/sepay-la-gi.html)
- A guest viewing a QR on the **same phone** cannot scan its screen with that phone’s camera. A supported banking-app deep link can solve this—[ZaloPay documents bank-app deep links](https://docs.zalopay.vn/vi/docs/guides/payment-acceptance/zalopay-emvco-qr/intro/)—or the guest may use an app’s image-import feature, another phone, or staff help. Image import varies by bank app: **UNVERIFIED** as a universal route. An unsupported deep link, switching apps, an edited amount, or a delayed confirmation can strand checkout.

**4. Legal and compliance**

- [Decree 254/2026](https://xaydungchinhsach.chinhphu.vn/toan-van-nghi-dinh-so-254-2026-nd-cp-ve-hoa-don-dien-tu-chung-tu-dien-tu-119260713164251972.htm), effective 1 July 2026, governs current e-invoicing. Letting a guest pay alone does not remove the restaurant’s invoice duties. Invoice timing and corrections need aligning with the confirmed sale and existing provider; a payment-success page is not itself an e-invoice. [Government explanation of timing](https://xaydungchinhsach.chinhphu.vn/thoi-diem-lap-hoa-don-khi-thuc-hien-kinh-doanh-hang-hoa-dich-vu-co-gi-moi-119260728152001421.htm)
- [Decree 52/2024](https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-52-2024-nd-cp-41938.htm) regulates non-cash payment services. If guests provide a phone number, location, allergy note or company invoice details, the [Personal Data Protection Law](https://vanban.chinhphu.vn/?classid=1&docid=214590&pageid=27160&typegroup=) and [Decree 356/2025](https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-356-2025-nd-cp-468371.htm) apply to that processing. Data requested for a bill should not silently become a marketing list.
- QR ordering must not bypass alcohol controls. The [2019 alcohol-harms law](https://congbaocdn.chinhphu.vn/CongBaoCP/VanBan/2019/6/29251/27285-1-2019559-56044-2019-qh14.pdf) prohibits selling, supplying or promoting alcohol or beer to anyone under 18. A phone order alone does not establish the buyer’s age; an age-sensitive handoff still needs a staff check.

**5. Failure modes and controls**

- A paid guest may see “pending” if the bank or webhook notification is delayed. A return page alone is insufficient evidence: [MoMo’s developer guidance](https://developers.momo.vn/v3/docs/payment/api/result-handling/notification/) explicitly recommends server notifications because a customer can close the browser or lose the return path. Webhook signatures, unique transaction references and a check of the actual credited amount help distinguish real payment from duplicate, wrong-amount or replayed notifications. [payOS webhook fields](https://payos.vn/docs/api/)
- If a guest pays twice, a dish is cancelled after payment, or the bill changes, the restaurant needs a visible exception and refund route. [KiotViet’s QR guide](https://www.kiotviet.vn/huong-dan-su-dung-kiotviet/fnb-thuc-don-dien-tu/goi-mon-qua-ma-qr/) explicitly shows unpaid orders requiring staff collection and prepaid cancellations requiring cashier refund. Prank orders are a greater risk with unattended static table codes; staff confirmation, limited table sessions or a location check are possible controls, each with friction. During an internet outage, the guest web page and automated confirmation may be unavailable even if the shop can continue local paper or device service—an **inference** to test, especially at Phú Nhuận.

## B. Change request from Mai Anh

Hi—I need to change something we signed off. I said table QR ordering could wait, but I’ve been watching our Friday dinners and looking at how other restaurants let guests add dishes from their phones. At our tables, people often decide on another meat portion or drinks while the server is busy elsewhere. I’d like guests to scan at the table, see what they’ve ordered and where their dishes are, then pay their bill by bank transfer themselves if they want to.

I don’t want to lose the personal service. Older guests may still ask Lan’s team to order, and Đức is right to worry about an allergy note or a strange order going straight to the grill. I also don’t want a phone saying “served” when the plate is still in the kitchen, or a guest leaving because their banking app says paid while Huy sees nothing. Please look at those situations with them.

I know this is a change from our baseline, and the pilot is now around **22 February**, after Tết—not January. I don’t expect you to promise all three new things by then without checking the cost and the bank connection. Show me what we could safely try at Bình Thạnh, what would wait, and what it would add to the build and monthly cost before I decide.

---

## Session 9 — CR-01 impact workshop (Mai Anh, Lan, Đức, Huy, Hạnh; Thanh and Quyên by phone)

### BA questions

Participants: Mai Anh, Lan, Đức, Huy, Hạnh; Thanh and Quyên join by phone for their items. Back in character. Answer in their voices, label the speaker, let people disagree, give concrete examples and numbers, "not sure yet" is fine. Around 1800-2600 words in total. Do not write requirements for me.

Mai Anh (owner)
O1. What should QR ordering achieve, in numbers you would check after a month (for example share of add-on orders placed by guests, fewer server trips, spend per guest, time from "bill please" to paid)?
O2. What would you try first at Bình Thạnh around 22 February, and what waits: guests only adding dishes after staff took the first order, full self-ordering, live status, self-payment? All tables or some areas? Lunch and dinner?
O3. Should guests be able to leave a phone number? Would marketing consent be a separate choice?
O4. How much extra one-off and monthly cost would you accept for this, and what would you postpone if it does not fit?

Lan (manager)
L1. Should a table QR work only after staff have seated the guests and opened the table, or may guests start by themselves (for example walk-ins who sit down)?
L2. Should guest orders go straight to the kitchen or wait for staff confirmation: first round only, every round, or only some cases (beer, allergy notes, large quantities, hotpot sets)? Who confirms, and how long can a guest order wait before someone confirms it?
L3. Prank or remote orders from a photographed QR: which protection would guests accept (staff confirmation, a short code on the table card that changes each seating, location check)?
L4. Several phones at one table: one shared cart or separate orders? What should happen when two friends order the same dish a minute apart?
L5. Until when may a guest cancel a dish they ordered on the phone?
L6. Which buttons do guests need (call staff, request the bill, ask for ice/tissues, other)? How fast must someone respond?
L7. Guests without smartphones or who prefer a server: anything different from today?

Đức (head chef)
K1. Which dish statuses can you honestly show to guests? In the pilot you keep printed tickets: who would mark "cooking" or "ready"? Would you bump dishes on a screen at least for "ready", or should guests only see "received / being prepared / served", with "served" marked by the runner?
K2. An allergy note typed by a guest: should that order be held until a server has spoken to the guest and you have reviewed it?
K3. Guests often order everything at once (hotpot, grill, rice): should guest orders fire immediately or be paced by staff?
K4. The kitchen closes at 22:00. Should the QR menu stop accepting food after last order (drinks still allowed)? Any exceptions for large bookings?

Huy (cashier)
C1. Self-payment: when may a guest pay (any time, or after asking for the bill)? The whole bill or only their share? Cash and cards still at your desk?
C2. What must you see on your screen for self-paid bills, unmatched transfers, and "the guest says they paid but nothing is confirmed"?
C3. After a guest pays: who releases the table, what receipt do they get, and what happens if they leave before the payment is confirmed?
C4. Deposits and discounts: should the guest see them on the phone bill?

Hạnh (accountant)
F1. Automatic confirmation: which route would you accept for the Vietcombank collection account: the bank's own service, an intermediary that reads transactions (payOS/SePay/Casso type), or a payment gateway (MoMo/VNPAY) with fees? Any rule about third parties seeing bank data, and what monthly or per-transaction cost is acceptable?
F2. Guests typing company name, tax code and email for the invoice on their phone: acceptable? Who checks it and how are mistakes corrected?
F3. Duplicate payment, overpayment, a dish cancelled after payment: what is the refund route and how quickly must it be settled?
F4. Beer ordered by phone and customers under 18: what check do you want?

Thanh (Phú Nhuận, by phone)
T1. Guests use their own mobile data, but if the shop's connection drops the kitchen cannot receive phone orders. Is it acceptable that QR ordering pauses during an outage and staff take orders instead? How should guests be told?

Quyên (District 3, by phone)
Q1. Private room and parties with pre-ordered sets and a minimum spend: should QR ordering be allowed there, only for add-ons, or not at all? Could each paying company settle its own share by QR?

All
Z1. What could go wrong with this change that we have not discussed?

### Client answers

**Mai Anh:** Thanks for getting everyone together. This is a change I’m asking you to assess, not permission to squeeze three untested features into February at any cost.

### Mai Anh

**O1 — What I would measure**

**Mai Anh:** I’d like to see whether guests actually use it. After a month, perhaps **one in four add-on orders** at the trial tables could come from guests’ phones. I’d compare missed or duplicated additions with what Lan logs now, and ask servers whether they spend less time going back just to take one more drink order. I won’t promise a staff reduction from that.

For payment, Huy says a guest can wait five to ten minutes at the rush just to ask for and settle a bill. If self-payment works, I’d hope the time from “bill please” to confirmed payment is closer to three minutes for those guests. I would watch spend per guest, but I don’t want staff pushing extra dishes merely to make that number rise. First we need a week or two of honest measurements, because these are my impressions.

**O2 — First trial and what waits**

**Mai Anh:** I’d start at **six tables at Bình Thạnh**, a mix of indoor and covered outside tables. Staff would take the first order and explain the QR; guests could use it for additions. Try lunch first, then a real Friday or Saturday dinner with Lan watching. I don’t want all 24 tables converted on 22 February.

I’d like guests to see that an order has been received. Show more dish progress only when Đức and the runners can keep it accurate. For self-payment, let Huy and Hạnh test real bank confirmation before offering it to those six tables. If the bank arrangement is not ready, that part waits; we keep the bill-specific QR work already discussed and staff verify transfers. Full self-ordering from the first dish can wait until we know add-ons behave properly, particularly for hotpots.

**O3 — Phone numbers**

**Mai Anh:** A guest may leave a phone number if they want an invoice contact or a way for us to follow up on a payment problem, but I don’t want it required simply to order a plate of food. Marketing must be a separate, clear choice. A booking number or invoice email does not mean they agreed to birthday messages.

**O4 — Cost**

**Mai Anh:** I might accept roughly **VND 50–80 million extra once**, if the whole build remains within the VND 600 million ceiling I mentioned. For fixed monthly costs, perhaps another **VND 1–2 million** if the total for hosting and support still fits around VND 12 million. Show me a realistic transaction-fee estimate separately. Those are limits for discussion, not a purchase approval.

If it doesn’t fit, I’d postpone the waiting list, loyalty launch and direct delivery-app connections before I sacrifice reliable ordering, shift closing, payment matching or prep transfers. I would also postpone *full* guest self-ordering rather than rush it into a busy-night pilot.

### Lan — Bình Thạnh manager

**L1 — Opening a table**

**Lan:** Staff should seat the guests and open their table before guest ordering works. Walk-ins sometimes sit at an available table before I reach them, especially outside; I’ll explain that someone must welcome them first. I don’t want an order arriving for a table that the floor plan still says is empty. If a group moves, the server needs to move its active bill and QR ordering together.

**L2 — Confirming guest orders**

**Lan:** For the six-table trial, I want a person to confirm **every phone order** before it reaches the kitchen. That person could be the server covering the area, with Huy as backup at the counter. I know Mai Anh wants fewer trips, but confirming on a device is quicker than walking over to take an order and then handing Huy paper.

**Mai Anh:** If every extra bottle of water waits for approval, won’t guests ask why they bothered?

**Lan:** Maybe. After we see a week of orders, I’d consider routine add-ons going through automatically. I would still hold beer until someone can check the guest if needed, and hold allergy notes, unusually large quantities, changed hotpot sets or anything saying “serve later.” I’d want ordinary requests checked within about a minute; if one sits for several minutes, it is worse than a server taking it.

**L3 — Remote or prank orders**

**Lan:** I would rather give a short code for the current seating than ask every guest for their phone location. A permanent QR sticker is convenient, but someone can photograph it. We can put the seating code on a small card when we greet the table and change it when they leave. I don’t know yet whether that adds too much work for the host. For the trial, staff confirmation also gives us a chance to catch an order that looks wrong.

**L4 — Several phones**

**Lan:** Each guest can send their own items, but they should appear on **the same table bill**, with separate additions we can identify. I don’t want one shared cart that one friend empties while another is still choosing. If two phones send the same hotpot set one minute apart, ask us to check before making both. If they order two beers, though, that may be exactly what they meant; the system mustn’t silently remove one.

**L5 — Cancelling**

**Lan:** A guest can withdraw an order while it is still waiting for our confirmation. After we have sent it to Đức, they need to call us, and the existing manager-and-kitchen discussion applies. I cannot promise “cancel” on a phone after meat is on the grill.

**L6 — Help buttons**

**Lan:** Call staff and request the bill are useful. Ice, tissues or extra dipping sauce could be simple requests, but keep the screen short. We should aim to acknowledge a call within two or three minutes, faster if someone flags an allergy or payment trouble. I would not show a promise that food will arrive in a precise number of minutes.

**L7 — Guests who want a server**

**Lan:** Nothing should change for them. Servers still carry handhelds, explain dishes and take orders. At a family table, one younger guest might enjoy the QR while their parents speak to the server. Both paths must end up on one bill. My worry is staff assuming that a QR table no longer needs attention.

### Đức — head chef

**K1 — Honest dish status**

**Đức:** “Received by the kitchen” is fair once the order is actually accepted and the ticket has printed. “Served” is fair when the runner marks that the dish reached the table. “Cooking” is less honest if nobody touches the screen when they start. During a Friday rush, my grill cooks won’t stop to mark every portion “cooking.”

We already planned a status view at the pass alongside printed tickets. I’ll try having the person at the pass mark a dish **ready** as it leaves the station, and the runner mark **served** after delivery. Test whether they remember. If they don’t, show the guest fewer statuses, not a false “ready” message while the plate is still waiting beside me.

**K2 — Allergies**

**Đức:** Yes, hold that order. A server must speak to the guest, including how serious the allergy is, then speak to me. A typed “no peanuts” note does not tell me whether traces from shared equipment are dangerous to them. I may suggest another dish or say we cannot safely serve it. The phone must not tell a guest that I have guaranteed an allergen-free meal.

**K3 — Pacing**

**Đức:** At a new table the server should talk through the meal and decide what to send first. Some guests tap every dish at once but expect rice after the grill and hotpot later. If those all print together, we either cook too early or spend time asking what they meant. Add-on drinks or a side dish are simpler. If guests use the phone for a hotpot set or several courses, staff should check timing before it comes to my stations.

**K4 — Closing the kitchen**

**Đức:** We normally close the kitchen at 22:00. I’d stop ordinary food orders around **21:45**, with the last-order time made clear to guests; bottled drinks can continue a little later while we’re open. For a pre-arranged large booking, Lan sometimes agrees with me to keep cooking longer. That must remain a manager-and-chef decision, not something a guest’s phone assumes.

### Huy — cashier

**C1 — When and how guests pay**

**Huy:** For the first trial, a guest should request the bill, then pay the **finalised whole bill**. Please don’t let them pay while a server is adding dishes or Lan is resolving a complaint. Splitting by item, or having two people transfer their shares, still comes through me until we’ve tested it. Cash and card remain at my desk; this is an option, not the only exit.

**C2 — What I need to see**

**Huy:** I need to distinguish “guest opened payment,” “bank confirmation pending,” “confirmed for this bill,” and “money arrived but we can’t match it.” Show the expected amount and bill number beside the bank transaction. If the guest says they paid and nothing has arrived, I want to check the actual company account or ask Lan to help—not press a button that marks it paid because of a screenshot.

Our QR today points to one account for all three shops. An amount alone isn’t enough when two tables owe VND 650,000. And if a guest pays VND 65,000 by mistake, I want the short payment visible, not an apparently settled bill.

**C3 — After payment**

**Huy:** The phone can show a payment acknowledgement and let the guest view their bill, but Hạnh’s e-invoice is separate. We can print a receipt if they want one. The runner or host should check the payment state before clearing and releasing the table. I wouldn’t expect a guest to stand at my desk just to leave, but staff still need to see the table is finished.

If someone walks out while confirmation is pending, I’ll leave the bill open and tell Lan immediately. We may have a booking phone number, but we often won’t. Later bank confirmation must still be matched to that bill so we don’t call a guest who paid correctly.

**C4 — Deposits and discounts**

**Huy:** Yes. Show the original dishes, approved discount, deposit already paid, and the remaining amount clearly. A party host should not transfer the full bill because the phone hid a VND 1 million deposit. If a discount is still awaiting Mai Anh’s decision, don’t offer the old amount as a final self-pay bill.

### Hạnh — accountant

**F1 — Confirmation route**

**Hạnh:** I would ask Vietcombank first what it offers for our **company collection account**, with written terms, pricing and transaction data. If that doesn’t meet our timing or budget, I would consider a provider such as payOS, SePay or Casso. I need to understand which bank data it receives, where it stores it, who can access it, how we revoke access and how it proves a notification is genuine. I will not give anyone our online-banking password as an integration shortcut.

A gateway may make checkout easier but can introduce different fees and settlement records. I need quotes and a realistic calculation for our volume. Perhaps VND 1–2 million a month for the three shops is discussable, but I cannot approve a per-transaction charge without seeing the monthly total. Whatever we choose, the bank credit and bill must still reconcile.

**F2 — Company invoice details**

**Hạnh:** Guests may type a company name, tax code and email on their phones. Let them review it carefully. I still want my team to check unusual details before issuing through meInvoice. A typo after issue follows the invoice correction process; it is not fixed by editing the guest’s web page. Don’t require those fields from an ordinary family paying its bill.

**F3 — Extra or reversed payments**

**Hạnh:** A duplicate transfer, overpayment, or refund after a cancelled dish must remain visible as money received and money returned. I’d prefer to refund to the originating account after verifying the bank transaction, rather than hand out cash against a screenshot. For a straightforward confirmed duplicate, I would aim to settle it by the next working day; more complicated cases may take two. If a dish is cancelled after the bill was paid, Lan records the reason, Huy confirms what was served, and I handle the refund and invoice correction. No one should erase the original payment.

**F4 — Beer and age**

**Hạnh:** A phone order proves nothing about age.

**Lan:** A server should check if there is any doubt **before beer is served**, and a beer request should wait for staff attention in the trial. We shouldn’t store photographs of identity documents just to sell a bottle.

### Other outlets by phone

**T1 — Thanh, Phú Nhuận:** Yes, QR ordering can pause when our shop loses its connection. Staff must immediately go back to their handheld or numbered paper slips. Tell guests in person and, if the phone page is still reachable, show that ordering is paused. Don’t let a guest keep pressing “send” while the kitchen receives nothing, then deliver all those taps after we recover. I want to practise that failure with my team before March.

**Q1 — Quyên, District 3:** For private-room parties, I’d allow **add-ons after I’ve confirmed the pre-ordered sets**. I don’t want 25 people reordering set components from their phones because they cannot see what the host already chose. Each paying company settling its own share by QR sounds attractive, but we first need the host and Hạnh to agree which food and invoice belong to which company. Please keep those large-party payments with Huy initially. Our terrace also moves indoors in rain, so a QR must follow the group’s bill, not keep sending additions to an empty terrace table.

### What else could go wrong?

**Lan:** Someone could put a fake QR sticker over ours and send guests to another payment page. Staff need to notice damaged or replaced table cards.

**Đức:** Good photos could make guests order hotpots and grill sets faster than we can cook them. The kitchen capacity hasn’t changed because the ordering screen did.

**Huy:** I’m worried about a guest seeing “paid” on their bank app while our confirmation is late, then paying again at my desk. I need a way to pause and check before asking for a second payment.

**Mai Anh:** And please remember our February pilot is already carrying a lot of change. I want a small guest trial we can stop if it confuses service, while the confirmed ordering and closing work stays stable.

---

## Session 10 — CR-01 confirmation (email)

### BA confirmation items

Scope and phases
Q1 Phase QR-1 (Bình Thạnh trial, from about 22 February, 6 tables mixing indoor and covered outside; lunch first, then one supervised Friday or Saturday dinner): staff seat guests, open the table and take the first order; guests use the QR only for add-on dishes and drinks; every phone order waits for staff confirmation (target: confirmed within 1 minute); guests see only "waiting for staff", "received by kitchen", "ready" (marked at the pass) and "served" (marked by the runner). A manager can switch QR ordering off per table or for the whole shop at any moment.
Q2 Self-payment (whole, finalised bill only) is enabled at those 6 tables only after Huy and Hạnh have tested real automatic confirmation with the chosen bank route. Until then the bill-specific QR is used with staff verification, as already agreed.
Q3 Phase QR-2 (review after 1-2 weeks of QR-1 data; not before the end-of-March rollout of the core system is stable): possible auto-acceptance of routine add-ons, more tables, full self-ordering from the first dish, split self-payment. Each needs your separate go-ahead. Private-room parties: add-ons only after Quyên confirms the pre-ordered sets; large-party and per-company payments stay with Huy.
Q4 Never automatic, even in QR-2: beer and alcohol, allergy notes, unusually large quantities, changed hotpot sets, "serve later" requests. They always wait for staff.

Rules
R1 A table QR only works while the table is open in the system; moving a group moves its bill and its QR ordering together.
R2 BA proposal on prank/remote orders: in QR-1, staff confirmation plus "table must be open" is the control; a short seating code (shown to the server on the handheld and printed on the first kitchen/bill slip, changing each seating) becomes mandatory only if routine add-ons are ever auto-accepted in QR-2. OK?
R3 Guests order from their own phones into the same table bill; each phone's items are identifiable; no shared cart. A second hotpot set (or other flagged item) from the same table within 2 minutes is highlighted to the confirming staff member; nothing is removed automatically.
R4 A guest may withdraw an item only while it waits for confirmation; afterwards the existing manager/kitchen cancellation rules apply.
R5 Food orders on the QR stop at 21:45 (last-order time shown to guests); bottled drinks continue until the kitchen/bar closes; exceptions for large bookings are set by the manager with the head chef, never by the guest.
R6 Guest buttons: call staff, request bill, and a short list of simple requests (ice, tissues, sauce). Target acknowledgement within 2-3 minutes; allergy or payment problems are flagged as urgent. No promised cooking times.
R7 Self-payment is offered only for a finalised bill with no pending approvals; the phone shows dishes, approved discounts, deposit already paid and the remaining amount. A payment counts only when the bank route confirms the exact amount with the bill reference; short or unmatched payments stay visible to Huy as "partly paid" or "unmatched". The cashier screen shows the states: payment opened, confirmation pending, confirmed, partly paid, unmatched.
R8 If a guest says they paid but confirmation is pending, staff must check the company account before asking for any second payment. A guest who leaves while confirmation is pending: the bill stays open, Lan is told, and a later confirmation is matched to that bill.
R9 Staff check the payment state before clearing and releasing the table.
R10 Duplicate transfers, overpayments and refunds after a cancelled dish are recorded as money received and money returned, refunded to the originating account after verification, target next working day (up to two for complex cases); original records are never erased.
R11 Guests may optionally enter company invoice details on the phone and review them before submitting; Hạnh's team checks unusual details before issuing in meInvoice; corrections follow the invoice process. Phone number is optional (invoice contact or payment follow-up); marketing consent is a separate, unticked choice.
R12 If the shop loses its connection, QR ordering pauses immediately (the phone shows "ordering paused, please ask our staff" if reachable); taps made during the outage are never delivered later; staff switch to handhelds or numbered paper.
R13 Anti-fraud: table QR cards are tamper-evident and checked by staff at each opening; the payment page is only on the restaurant's own domain and shows the company account name; guests are told to pay only to that name.

Targets and cost
G6 After one month of QR-1: about one in four add-on orders at trial tables come from guest phones; missed/duplicated additions compared with Lan's log.
G7 For self-paying guests: "bill please" to confirmed payment in about 3 minutes (today 5-10 minutes at the rush, to be measured for 1-2 weeks first).
C1 Budget guard: extra one-off VND 50-80 million only if the total build stays within VND 600 million; extra fixed monthly VND 1-2 million only if the fixed total stays around VND 12 million; transaction fees estimated separately for your approval. If it does not fit, postpone waiting list, loyalty launch, direct delivery-app connections and QR-2 before touching ordering, shift closing, payment matching or prep transfers.
C2 Bank route: Hạnh asks Vietcombank first for written terms for the collection account; otherwise evaluate payOS/SePay/Casso-type providers on data access, storage, revocation, signed notifications and monthly cost. No one ever shares online-banking passwords.

### Client confirmation

### Scope and phases

- **Q1 — Correction. Lan:** Six tables, staff opening the table, first order taken by staff, and confirmation of every phone add-on are right for the February trial. One minute is a target we’ll measure during dinner, not a promise to guests. **Đức:** Show “ready” only if the person at the pass reliably marks it during the supervised trial. If that action is missed, show “waiting for staff,” “received by kitchen” and “served” instead. **Mai Anh:** Lan must be able to pause QR ordering without closing a table’s bill.
- **Q2 — Confirmed. Huy:** Real bank confirmation must be tested before guests can self-pay. Until then, staff verify the bill-specific QR transfer.
- **Q3 — Confirmed. Mai Anh:** QR-2 needs a separate decision after the core rollout is stable. **Quyên:** Private-room sets and company payments stay staff-led as stated.
- **Q4 — Confirmed. Đức:** Those orders need a person to review them. A typed allergy note also needs a conversation with the guest.

### Rules

- **R1 — Confirmed. Lan:** When guests move, the old table must stop accepting their orders.
- **R2 — Correction. Lan:** I want the short **seating code in QR-1**, as well as staff confirmation. An open table and a photographed permanent QR are not enough for me. Give the code to guests on a card when they sit down and change it for the next group; there is no need to print it on a kitchen ticket. Please test how much work this adds to greeting and clearing tables.
- **R3 — Confirmed. Lan:** Highlight a likely duplicate, but let staff ask the guests. Two identical dishes may be intentional.
- **R4 — Confirmed. Đức:** Once it reaches my kitchen, the guest asks staff; the phone cannot promise a cancellation.
- **R5 — Correction. Lan:** Stop ordinary QR food orders at 21:45. Bottled drinks can continue until about **22:15**, subject to the manager’s last call; we close at 22:30. Đức and I agree any large-booking exception ourselves.
- **R6 — Confirmed. Lan:** Keep the simple-request list short. The two-to-three-minute acknowledgement is an internal target.
- **R7 — Confirmed. Huy:** A short payment must show as partly paid, not as a settled bill. If money arrives with the wrong reference or an excess amount, I need to see it without the system guessing which table it belongs to. **Hạnh:** The deposit and approved discount must appear before the guest is offered the final amount.
- **R8 — Confirmed. Huy:** I’ll check the company account before suggesting another payment. A pending bill remains open if the guest leaves.
- **R9 — Confirmed. Lan:** Staff check before clearing the table; we must also avoid holding a paid guest because a screen is slow to refresh.
- **R10 — Confirmed. Hạnh:** Record both movements. Next working day is the aim for a straightforward verified duplicate, not a guarantee before we identify the payer.
- **R11 — Confirmed. Hạnh:** Company invoice details are optional and reviewable. Marketing consent is separate and unticked.
- **R12 — Correction. Thanh:** “Immediately” assumes the guest page knows our shop connection has failed. When we detect an outage, I’ll pause QR ordering, tell guests, and put out the staff-ordering fallback. If the page is reachable, it should say ordering is paused. An order attempted during the outage must not surprise the kitchen after recovery.
- **R13 — Confirmed. Lan:** We’ll check table cards when opening a table and replace a damaged one. **Hạnh:** The genuine payment page must show our company name and account; staff should help a guest who sees something different.

### Targets and cost

- **G6 — Confirmed. Mai Anh:** Count add-ons at the *six trial tables*, not across the whole restaurant. Compare missed and duplicated additions with Lan’s log; the 25% is a learning target, not a reason to pressure guests.
- **G7 — Confirmed. Huy:** Measure the present wait first. Record cases where bank confirmation is delayed separately so a quick page load isn’t mistaken for a completed payment.
- **C1 — Confirmed. Mai Anh:** These are budget guards, subject to a costed proposal. Keep the March essentials we already agreed. QR-2 is the first part of this change to defer if money or time runs short.
- **C2 — Confirmed. Hạnh:** Get Vietcombank’s written offer for our company account first, then compare authorised providers. Include account coverage, delays, data access and revocation, notification verification, and the expected monthly bill. No shared banking passwords.

**Mai Anh:** I approve **CR-01 for inclusion in the design with the corrections above**. That is not approval to buy the service or build the extra scope. I want the two-week technical check and a revised total price before making that commitment.

---

## Change request CR-02 — Hanoi context and staff scanning of the table QR: field research (Codex, out of character), relocation mapping, message from Mai Anh

### BA request

Background: the project team has decided that the client's restaurant chain is located in **Hanoi**, not Ho Chi Minh City. Keep the same business profile, people, numbers and every decision already confirmed (Sessions 1-10, CR-01), unless Hanoi reality genuinely changes a fact. The team has also decided that staff will use the same table QR as guests: a server scans the table QR with the staff app to open that table and order on the guests' behalf.

# PHASE 1 - FIELD RESEARCH ON HANOI (live web search, 2025-2026)

Research, with sources:
1. Hanoi casual dining (Vietnamese family dishes, grill/BBQ, hotpot, beer-and-food venues, small chains of 2-5 outlets): typical areas and customer mix (office lunches, family dinners, students, weekend groups), table counts and rents where available, and **seasonality** (winter peak for hotpot and grill, summer, rainy season, Tet).
2. Hanoi local rules and practice that affect a restaurant's operations: **use of sidewalks (via he) for tables** and any sidewalk-use fees or permits; motorbike parking arrangements for guests; noise and late-night operation; fire-safety inspections for restaurants; food-safety certificates. Also the **2025 administrative reorganisation** (two-level local government, district level abolished, new ward names), and how people still refer to old district names.
3. Guests' payment and ordering habits in Hanoi: bank-transfer QR vs cash vs cards vs e-wallets; company VAT invoice requests from offices; delivery apps active in Hanoi; the effect of strict drink-driving enforcement (Decree 168/2024) on beer sales and on guest behaviour.
4. QR and tablet ordering in Hanoi restaurants and chains; and whether POS vendors (iPOS/FABi, KiotViet, Sapo FnB, MISA CukCuk, POS365, others) let **staff scan the table QR** with a staff app to open the table and order, and how guest and staff orders are combined.
5. Staffing context in Hanoi (part-time students, turnover) only as far as it affects training and device use.

Prefer primary/authoritative sources; mark anything unverifiable as UNVERIFIED; do not invent citations.

# PHASE 2 - RELOCATION MAPPING (still out of character)

Map the three outlets to realistic Hanoi locations **with the same operational characteristics**:
- the oldest and busiest outlet (pilot, 24 tables, back-room shared prep kitchen for all outlets);
- the outlet with unstable internet, a screened-off four-table group section, front tables and a larger delivery share (20 tables);
- the outlet with a three-table private room and a four-table terrace or rooftop (22 tables).
For each give: new outlet name and short code (2-3 letters), the new ward name (post-2025) and the old district name people still use, and whether any fact must change for Hanoi realism (for example outdoor/sidewalk tables, the "covered outside area", weather moves). Keep managers' names (Lan, Thanh, Quyên) and staff names. Also list which previously confirmed facts change because the chain is in Hanoi (seasonality, the pilot date sitting in the Hanoi winter peak, outdoor seating rules, anything else) and which stay the same.

# PHASE 3 - BACK IN CHARACTER (Mai Anh)

Write Mai Anh's short, informal message to the BA confirming the move to Hanoi context (as if it had always been so, but acknowledging that some earlier details were described differently) and reacting to the idea that servers will scan the same table QR as guests. Mention what she likes and what worries her. Do not write requirements.

# OUTPUT (stdout), exactly this structure
## A. Hanoi field research brief
(bullets by topic 1-5, sources inline, about 900-1300 words)
## B. Relocation mapping and changed facts
(a table plus short bullet lists)
## C. Message from Mai Anh
(in character, short)

### Codex output

## A. Hanoi field research brief

**1. Casual dining, locations and seasonality**

- A three-outlet Vietnamese family-meal, grill and hotpot chain is plausible across the older Đống Đa, Cầu Giấy and Hai Bà Trưng areas. Cầu Giấy’s ward profile describes a mix of residential, education and technology activity; that supports an office-lunch and student-customer scenario. Inner Đống Đa and Hai Bà Trưng support residential dinners and groups, but a precise customer mix for any fictional address is **UNVERIFIED**. [Cầu Giấy ward profile](https://caugiay.hanoi.gov.vn/gioi-thieu-chung/gioi-thieu-chung-2805250626170626104.htm), [Đống Đa ward profile](https://dongda.hanoi.gov.vn/gioi-thieu-chung-62989/gioi-thieu-chung-ve-phuong-dong-da-thanh-pho-ha-noi-2806250627173844843.htm)
- I found no reliable Hanoi-wide “typical tables per family restaurant” figure. Keeping this chain’s **20–24 tables and 80–96 seats per outlet** is a scenario fact, not a market average. Rents depend heavily on frontage, floor area and floors: 2025 reporting found advertised premises on several old Hai Bà Trưng streets at roughly **VND 15–75 million/month**, but those listings are not a rent estimate for these three outlets. [Lao Động reporting](https://news.laodong.vn/bat-dong-san/o-at-cho-thue-mat-bang-tren-cac-tuyen-pho-quan-hai-ba-trung-1469529.ldo)
- Hanoi’s cold season makes hotpot and grill particularly attractive, but a quantified winter sales uplift for comparable restaurants is **UNVERIFIED**. Cold rain can also empty exposed tables: November 2025 reporting described both chilly weather and fewer customers at some eateries. Summer heat and the May–October rainy season make shaded, indoor and movable seating important. Tết changes staffing and trading days. A pilot around **22 February 2027** would therefore still be in Hanoi’s cool-weather hotpot season, shortly after the chain’s planned Tết closure; its precise demand must be judged from this chain’s bookings. [November weather reporting](https://thanhnien.vn/ha-noi-ret-dam-nhiet-do-giam-thap-nhat-tu-dau-mua-185251118192550695.htm), [government climate description](https://nrsd.mae.gov.vn/Pages/chi-tiet-tin-tuc.aspx?ItemID=2441), [Hanoi’s February 2026 account of Tết’s effect on activity](https://www.hhtip.hanoi.gov.vn/vi/tin-tuc/ha-noi-sa-n-xua-t-cong-nghie-p-2-thang-dau-nam-2026-tang-9-4-so-voi-cung-ky-2705.html)

**2. Local operating constraints**

- Tables on a public sidewalk (*vỉa hè*) cannot be assumed lawful because a restaurant pays rent or because other eateries put tables there. Hanoi has enforced against unauthorised food-service use of sidewalks. Temporary-use and fee rules are area- and purpose-specific; a fee for an approved parking area is **not** blanket permission for restaurant seating. A 2026 proposal concerning business use of sidewalks was still under discussion in the sources reviewed, so I could not verify a general dining-table permit or fee applicable to these fictional sites: **UNVERIFIED**. [Hanoi enforcement example](https://longbien.hanoi.gov.vn/kinh-te-ha-tang-do-thi/long-bien-tang-cuong-xu-ly-vi-pham-trat-tu-do-thi-qua-hinh-anh-2811260508091636474.htm), [city discussion of sidewalk-use rules](https://cuanam.hanoi.gov.vn/thong-tin-cong-khai/via-he-ha-noi-thong-thoang-ho-kinh-doanh-mong-som-co-co-che-thue-2801260509163241532.htm), [2026 parking-fee amendment](https://cmshn.hanoi.gov.vn/tin-tuc-su-kien-noi-bat/ha-noi-phi-su-dung-tam-thoi-long-duong-he-pho-cao-nhat-400000-dong-m2-thang-4260511120548211.htm)
- Guest motorbike parking needs space on the premises or a lawful nearby arrangement; putting bikes on a pavement can itself draw enforcement. The three shops’ actual parking capacity and agreements are **UNVERIFIED** and would need a site check. Outdoor seating also raises neighbour-noise issues. I found no sound basis for claiming that **all** Hanoi restaurants must close at 22:00; this chain’s 22:30 closing time should instead be checked against each site’s permissions, lease and applicable noise limits. [Giảng Võ ward’s enforcement guidance](https://giangvo.hanoi.gov.vn/thong-tin-cong-khai/cac-muc-phat-pho-bien-voi-hanh-vi-lan-chiem-via-he-2808260512095606864.htm), [national environmental-noise standard](https://datafiles.chinhphu.vn/cpp/files/vbpq/2025/5/01-bnnmt-1.pdf)
- Fire safety and food safety are operating obligations, particularly with charcoal grills, hot equipment, a shared prep room and a rooftop/terrace. Hanoi assigns ward-level fire-safety inspections, including checks prompted by signs of violation or complaints. Food-service establishments may need a food-safety eligibility certificate; exemptions exist for some categories but do not remove hygiene duties. The applicable certificates and inspections for these fictional premises are **UNVERIFIED**. [Hanoi fire-inspection rules](https://xaydungchinhsach.chinhphu.vn/ha-noi-quy-dinh-nhiem-vu-kiem-tra-ve-phong-chay-chua-chay-cua-ubnd-cap-xa-119260514163023154.htm), [Hanoi food-service certificate procedure](https://thanglong.chinhphu.vn/ha-noi-50-tthc-duoc-thuc-hien-tai-dai-ly-dich-vu-cong-truc-tuyen-103250604130455792.htm), [food-safety guidance](https://thanhliet.hanoi.gov.vn/y-te-giao-duc/huong-dan-dieu-kien-dam-bao-an-toan-thuc-pham-doi-voi-co-so-kinh-doanh-dich-vu-an-uong-269326042416490361.htm)
- Since **1 July 2025**, Hanoi has operated under two-level local government with new wards. “Old Đống Đa district” remains a useful conversational location description; formal current addresses should use the correct **ward and Hanoi**. The official profiles confirm wards named Đống Đa and Cầu Giấy within parts of their former districts; Hanoi also has a Hai Bà Trưng ward. [Đống Đa ward](https://dongda.hanoi.gov.vn/gioi-thieu-chung-62989/gioi-thieu-chung-ve-phuong-dong-da-thanh-pho-ha-noi-2806250627173844843.htm), [Cầu Giấy ward](https://caugiay.hanoi.gov.vn/gioi-thieu-chung/gioi-thieu-chung-2805250626170626104.htm), [Hai Bà Trưng ward notice](https://haibatrung.hanoi.gov.vn/tin-tuc-tong-hop-thong-tin-tuyen-truyen/xu-phat-vi-pham-hanh-chinh-doi-voi-dia-diem-kinh-doanh-nha-hang-vien-cong-ty-tnhh-hai-thanh-vien-newex-tai-dia-chi-so-3-ngo-ba-trieu-phuong-hai-ba-trung-thanh-pho-ha-noi-2814251207212917835.htm)

**3. Payment, delivery and beer**

- VietQR bank transfers are well established nationally: NAPAS reported nearly **90 million mobile-banking accounts** using banking apps to scan VietQR as of October 2025. That is **not** a Hanoi restaurant payment-share statistic. This chain’s earlier cash/transfer/card/wallet proportions should stay as its cashier’s estimates until transaction data confirms them. Office customers asking for company e-invoices is plausible in the chosen locations, but their request rate by Hanoi neighbourhood is **UNVERIFIED**. [NAPAS report](https://en.napas.com.vn/napas-2025-member-organization-conference-184260317124736875.htm), [Sapo’s customer-entered invoice details](https://help.sapo.vn/cai-dat-va-su-dung-ma-qr-cho-khach-hang-tu-nhap-thong-tin-xuat-hoa-don-tren-sapo-fnb)
- [GrabFood](https://www.grab.com/vn/en/merchant/), [ShopeeFood](https://shopeefood.vn/ha-noi/food) and [beFood](https://food.be.com.vn/ha-noi/bepfood-ga-ran-ga-luoc-ga-u-muoi-hoa-tieu-tran-thai-tong-105635) operate in Hanoi. The chain can keep GrabFood and ShopeeFood at all three shops and leave beFood as a possible later channel.
- [Decree 168/2024](https://xaydungchinhsach.chinhphu.vn/toan-van-nghi-dinh-168-2024-nd-cp-quy-dinh-xu-phat-vi-pham-hanh-chinh-ve-trat-tu-atgt-duong-bo-119241231164556785.htm) strengthened traffic-violation penalties, including drink-driving penalties. It is reasonable to expect some beer customers to choose a non-drinking driver, taxi or ride-hailing service, or drink less; a measured effect on **this chain’s** beer sales is **UNVERIFIED**. Beer remains on the menu, and the existing age-check and stock-control concerns remain.

**4. QR and staff tablet ordering**

- QR guest ordering and staff ordering on handhelds are documented for [iPOS FABi](https://fabi-docs.ipos.vn/), [KiotViet](https://www.kiotviet.vn/quan-ly-nha-hang), [Sapo FnB](https://help.sapo.vn/qr-order-tren-phan-mem-sapo-fnb), [MISA CukCuk](https://helpv2.cukcuk.vn/vi/kb/thiet-lap-goi-mon-tai-ban), and [POS365](https://www.pos365.vn/tich-hop-he-thong-kds-9006.html). These are national vendor products, not evidence of a distinct Hanoi workflow. iPOS’s documented guest QR flow sends requests to a staff handheld for confirmation; KiotViet documents guest QR additions being associated with a table bill. [iPOS handheld flow](https://fabi-docs.ipos.vn/), [KiotViet table-order guide](https://www.kiotviet.vn/huong-dan-su-dung-kiotviet/fnb-thuc-don-dien-tu/goi-mon-qua-ma-qr/)
- I could **not verify** that any of those vendors lets a **server scan the exact same physical table QR in a staff app to open the table and enter the first order**. Some documents describe staff selecting a table in an app; iPOS also describes scanning a *POS login/connection QR*, which is a different code. The team’s chosen same-QR staff flow remains valid project context, but should be demonstrated or built explicitly rather than attributed to a vendor. It must preserve the confirmed distinction between an authenticated staff order and a guest add-on awaiting confirmation. [iPOS staff-app documentation](https://fabi-docs.ipos.vn/), [Sapo service-app table ordering](https://help.sapo.vn/tao-don-hang-an-tai-ban-tren-ung-dung-sapo-phuc-vu)

**5. Staffing and training**

- Hanoi restaurants do recruit students for part-time kitchen and service shifts; a [Hanoi vocational college’s 2026 restaurant-chain posting](https://hnivc.edu.vn/he-thong-nha-hang-phuong-nam-co-co.html) explicitly sought part-time and full-time students. I found no robust local turnover rate for comparable small chains: **UNVERIFIED**. This supports retaining the agreed short role-based guides, personal staff logins and busy-night practice, especially for weekend-only staff.
- The chosen Hanoi wards are in **Region I** for the 2026 statutory minimum-wage schedule. That affects payroll compliance, but the project’s decision to keep timekeeping and payroll outside the first system is unchanged. [Decree 293/2025 summary](https://xaydungchinhsach.chinhphu.vn/nghi-dinh-so-293-2025-nd-cp-quy-dinh-muc-luong-toi-thieu-doi-voi-nguoi-lao-dong-lam-viec-theo-hop-dong-lao-dong-119251110172808433.htm)

## B. Relocation mapping and changed facts

| Outlet and code | Current ward; familiar former district | Manager and preserved operation | Hanoi-specific physical reading |
|---|---|---|---|
| **Khói Bếp Đống Đa — DDA** | **Đống Đa ward**; old **Đống Đa district** ([ward profile](https://dongda.hanoi.gov.vn/gioi-thieu-chung-62989/gioi-thieu-chung-ve-phuong-dong-da-thanh-pho-ha-noi-2806250627173844843.htm)) | **Lan**; oldest and busiest; **24 tables**, about 96 seats; two-person shared prep works in the back room; pilot outlet | The six covered outside tables are in a **covered courtyard within the leased premises**, not on the public sidewalk. |
| **Khói Bếp Cầu Giấy — CGY** | **Cầu Giấy ward**; old **Cầu Giấy district**, around the former Dịch Vọng area ([ward profile](https://caugiay.hanoi.gov.vn/gioi-thieu-chung/gioi-thieu-chung-2805250626170626104.htm)) | **Thanh**; **20 tables**, about 80 seats; four-table screened section; unstable connection; larger delivery share | The four front tables sit in a **recessed area of the premises**. They can be moved indoors during heat or rain. No sidewalk seating is assumed. |
| **Khói Bếp Hai Bà Trưng — HBT** | **Hai Bà Trưng ward**; old **Hai Bà Trưng district** ([ward example](https://haibatrung.hanoi.gov.vn/tin-tuc-tong-hop-thong-tuyen-truyen/xu-phat-vi-pham-hanh-chinh-doi-voi-dia-diem-kinh-doanh-nha-hang-vien-cong-ty-tnhh-hai-thanh-vien-newex-tai-dia-chi-so-3-ngo-ba-trieu-phuong-hai-ba-trung-thanh-pho-ha-noi-2814251207212917835.htm)) | **Quyên**; **22 tables**, about 88 seats; three-table private room; four outdoor tables | The four outdoor tables are on the property’s **terrace or rooftop**. Rain and cold still prompt whole-group moves; terrace capacity and fire-safety arrangements require a site check. |

**Changed because the chain is in Hanoi**

- Replace the former Ho Chi Minh City district labels, outlet addresses and local supplier locations with the Hanoi ward/old-district mapping above. Use current ward names for formal records; old districts remain useful when speaking to customers.
- Treat **cold-weather hotpot/grill demand**, Tết parties and cold or rainy outdoor-table moves as local planning factors. The post-Tết **~22 February 2027** pilot date remains agreed, now with winter demand to consider.
- Confirm that the covered, front and terrace seating is **within each property**, with lawful guest parking arrangements. No public-sidewalk tables or parking rights are presumed.
- For the agreed new staff flow, a server uses the **same table QR as guests**, but scans it in the authenticated staff app to open the table and place the first order. The guest uses it for the approved QR-1 add-on trial after seating.

**Unchanged**

- Business concept, opening years, all table and seat counts, staff names and roles, operating hours, estimated covers and average spend, POS365/paper/Excel/Zalo workflow, GrabFood and ShopeeFood use, company form, Vietcombank collection account, food-prep and payment problems, budget, sign-off conditions, the post-Tết pilot and March rollout goal, and every confirmed CR-01 control and phase decision.

## C. Message from Mai Anh

Hi—I need to correct the location in our notes. Khói Bếp is in **Hanoi**: Lan’s shop is in Đống Đa, Thanh’s in Cầu Giấy and Quyên’s in Hai Bà Trưng. I described the earlier locations differently, but the teams, shops and problems we walked through are the same. Our outside tables are on our own premises; I don’t want anyone planning to put tables on the pavement.

I like the idea that a server can scan the **same table QR** with their staff app to open the table and take the first order. It may save them searching through table numbers, and guests can then use that card for add-ons. My worry is a server opening the wrong table, especially when Quyên moves a terrace group inside, or a guest trying to order before staff have seated them. Lan also wants the seating code and staff checks we agreed for the guest trial.

Please keep the small **around-22-February** trial and its cost check as agreed. A cold evening with hotpot orders will tell us more than a quiet afternoon demonstration.

---

## Session 11 — CR-02 workshop: Hanoi context and staff scanning of the table QR (Mai Anh, Lan, Đức, Huy, Hạnh; Thanh and Quyên by phone)

### BA questions

Participants: Mai Anh, Lan (Đống Đa), Đức, Huy, Hạnh; Thanh (Cầu Giấy) and Quyên (Hai Bà Trưng) by phone. Back in character; answer in their voices, label the speaker, disagree where natural, concrete examples, "not sure" is fine. Around 1300-1900 words. Do not write requirements for me.

Mai Anh
O1. Hanoi's cold season: how do your busiest winter evenings differ (hotpot vs grill mix, covers, table turnover, office year-end parties)? Does it change anything about the ~22 February pilot or the six-table QR trial? What happens in the summer slump?
O2. Guest motorbike parking: on your own premises or a nearby service? Does parking involve anything the restaurant must record or pay per guest, or is it entirely outside our system?

Lan (Đống Đa)
L1. Walk me through seating a walk-in group of six with the new flow: the server scans the table QR with the staff app, opens the table, enters the guest count, gets the seating code for the guests' card, and takes the first order. Where is this faster or slower than picking the table on the floor plan today?
L2. Wrong-table risk: what should the staff app show or ask when a server scans a QR (large table number, zone, current state)? What should happen if the scanned table is already open for another group, reserved, or being cleaned?
L3. When a server scans a table that has guest QR orders waiting, should those orders appear right there so the server can check with the guests and confirm on the spot? For beer (age check) and allergy notes, should confirmation require that the server scanned that table (proof of being at the table)?
L4. Moving a group (for example from the courtyard indoors when it rains or gets cold): should the server scan the old table and then the new tables to move the bill and QR ordering together?
L5. Should the floor plan stay in the staff app, and for what (overview, damaged card, tables without a card)?
L6. Are QR cards needed on all 24 tables for staff scanning, even though guest ordering is only enabled at six trial tables?

Đức (head chef)
K1. Cold evenings with hotpot surges: anything to change in kitchen pacing, the guest-visible statuses, or the last-order times?

Huy (cashier)
C1. Should the cashier also be able to scan a table QR to pull up that table's bill quickly? Anything to prevent settling the wrong table's bill?

Hạnh (accountant)
F1. Does the Hanoi context change anything for tax, invoices, bank accounts or reporting? Are parking or premises fees entirely outside this system?

Thanh (Cầu Giấy, by phone)
T1. Staff scanning must keep working when the shop's internet is down, even though guest ordering pauses. Is that important for you? Any practical issue with scanning at night (lighting on front tables, card placement, phone cameras)?

Quyên (Hai Bà Trưng, by phone)
Q1. Terrace/rooftop in cold and rain: how often do groups move in winter, and would scanning old and new tables help? Should the private room have one QR card per table or one for the room?

All
Z1. What could go wrong with staff scanning the same QR as guests, or with the Hanoi context, that we have not discussed?

### Client answers

### Mai Anh

**O1 — Winter service**

**Mai Anh:** Cold evenings bring more hotpot orders. At Đống Đa, a strong winter weekend is near the top of the range I gave you—perhaps 160–175 dine-in guests—but tables do not necessarily turn faster. A group can stay longer around a hotpot. Đức says hotpot might be a third or more of dinner orders on a cold night; that is his estimate, not a sales report. January *tất niên* parties are especially busy. After Tết, we also get family and office groups meeting again, though I can’t predict what 22 February will be like.

I still favour the six-table trial after Tết. Choose a supervised dinner when the restaurant is genuinely busy, including a hotpot group, but don’t switch on guest ordering for every table just because the first lunch went well. In summer, hotpot sells less and the covered courtyard is less attractive in extreme heat or heavy rain. We sell more lighter dishes and drinks. I call it a slump for hotpot, not necessarily for the entire restaurant; I’d need Hạnh’s figures to compare seasons properly.

**O2 — Parking**

**Mai Anh:** At Đống Đa we can fit only a small number of motorbikes within our premises, certainly not one for every diner. We direct overflow to a nearby authorised parking service. The host stamps a parking slip for our guests, and the service bills us monthly for the slips used. We don’t add a parking charge to the customer’s restaurant bill. Lan keeps the slips and Hạnh checks that expense in accounting. Cầu Giấy and Hai Bà Trưng have their own nearby arrangements. I don’t want parking to become a new checkout step in this project, but the cost belongs in a truthful outlet P&L.

### Lan — Đống Đa manager

**L1 — Seating and the first order**

**Lan:** Take a walk-in group of six at an indoor table included in the guest trial. I greet them, check the table is available, and a server scans its card with the staff app. I’d expect the app to show that table, so the server confirms it, enters six guests, takes the first order and gives the group the current seating code for phone add-ons.

Scanning might save searching a crowded floor plan for the table number, especially for a new part-timer. It could also be slower if the card is greasy, poorly lit or hidden behind dishes. The server must still talk through portion sizes and hotpot timing. I don’t want staff standing over a group trying five times to focus a camera when they already know it is table 12.

**L2 — Wrong table or unavailable table**

**Lan:** Make the table number and zone very obvious—“Courtyard C3” rather than a tiny “3.” I want to see whether it is free, reserved, occupied or being cleaned before confirming anything. Scanning an occupied table should show the existing group and bill; it must not quietly open a second group. If it is reserved for later, I need the booking time visible so I can decide whether this walk-in can use it. Only a manager should change that decision. If cleaning is still in progress, the server should wait until we mark it ready.

Last month we nearly seated a walk-in at a table reserved for a group arriving 20 minutes later. A QR would not have solved that unless it showed the reservation at the moment of seating.

**L3 — Pending guest orders**

**Lan:** Yes. When the server scans a table during service, show any guest orders waiting for confirmation. Then they can ask, “Did you mean two beef portions?” while they’re beside the guests. That’s useful for the six-table trial because every guest order waits for us.

For beer, the server must be at the table and check age if there is doubt. Scanning there is useful evidence that they attended, but a scan alone does not prove who will drink it. For an allergy, the server has to speak to the guest and then Đức. I would not want an “allergy checked” button that skips that conversation. If a card is damaged, the manager needs another way to handle the table without pretending it was scanned.

**L4 — Moving guests**

**Lan:** I’d scan the old table to find the group, then the main new table and any other tables they occupy. I want to see the old and new table numbers together before I confirm the move. If rain sends six people from two courtyard tables to two indoor tables, their bill and active guest ordering should follow them. The old cards must stop taking their orders. I don’t want an addition arriving from someone’s phone for an empty courtyard table after they have moved.

**L5 — Floor plan**

**Lan:** Keep it. I use it to see the whole room, plan a large booking, find tables being cleaned and handle a missing card. A QR helps identify the table directly in front of me; it does not show where I can seat the next group or whether we have space to move outdoor guests.

**L6 — Cards on all tables**

**Lan:** Yes, staff should have a scan card at all 24 tables if this is how they open tables. But only the six trial tables should invite guests to order. Please make that difference clear on the card or when we speak to guests. Otherwise someone at a seventh table will scan and reasonably ask why their friends can order but they cannot. We also need spare cards when one is lost or damaged.

### Đức — head chef

**K1 — Cold-night pacing and status**

**Đức:** A cold night can bring several hotpot sets within ten minutes. The broth may be ready, but the complete set is not ready until the meat and vegetables are assembled. Please don’t tell guests “ready” when I have only started the pot. The person at the pass can mark it ready when the whole dish or set is there, and the runner marks it served after delivery. If the pass gets too busy to keep that accurate, use fewer guest-visible statuses, as we agreed.

I would not change the normal **21:45 last food order** or the later bottled-drink window just because a QR makes ordering easy. Lan and I can agree an exception for a booked party. During a hotpot surge I may ask her to slow a large group’s next course; a phone must not fire every “later” item at once.

### Huy — cashier

**C1 — Scanning a bill**

**Huy:** Scanning could help me pull up a table’s bill quickly, but I would still read back the table number and amount before taking payment. At dinner we might have two tables with similar totals, and several people may be standing near the desk holding table cards or photos. A scan should bring up the current bill, not automatically mark anything paid or close it.

If tables have been joined or moved, show me the group’s current bill and the tables included. That matters more than the original card number. For a company party, I also need to see whether a deposit was applied before I show the amount to pay.

### Hạnh — accountant

**F1 — Hanoi finance context**

**Hạnh:** Our company form, VAT method, invoicing duties, Vietcombank collection account and reconciliation problem do not change because the city name changes. The formal company and outlet addresses on invoices and provider records must use the correct current Hanoi wards. I’ll check those details; staff can still say “old Đống Đa district” when giving directions.

The parking service is an operating expense. I’ll keep its monthly invoice and Lan’s stamped-slip count in our accounting process. We are not charging each guest through the restaurant bill. If premises or parking fees change, that affects an outlet’s P&L, not how Huy records a hotpot sale. I would not assume we have permission to put either tables or bikes on a public sidewalk.

### Other managers by phone

**T1 — Thanh, Cầu Giấy:** Staff scanning during an internet outage is important to me. When our connection fails, guest phone ordering pauses, but the staff app should still recognise the table and let us send orders through the shop’s local setup. Otherwise this new scanning step would take away the offline service we already agreed was essential.

Our four front tables can be dim after sunset, and the cards may get wet when rain blows in. I’d want to try the actual staff phones or handhelds there at night. Put the card where a server can scan it without asking guests to move plates. If it cannot be read, we use the floor plan or table number and continue serving.

**Q1 — Quyên, Hai Bà Trưng:** On a pleasant evening, some groups choose the terrace specifically. In a cold or wet week, we may move two or three groups; in good weather we might go much longer without a move. Those are rough memories, not a log. Scanning the old and new tables could help Huy avoid the split bill we had after last month’s rain, provided it does not force me to move a group indoors when no table is actually free.

The private room has **three tables**, so I want one card per table. A single QR for the whole room would make it hard to tell where an extra drink or dish should go. The party’s pre-ordered sets still belong to the host’s booking, and guest QR use there remains add-ons only after I confirm those sets. Several companies paying for one party will still go through Huy and Hạnh.

### All — other risks

**Lan:** Someone could swap two cards while cleaning. The printed table name, what the staff app shows and the floor plan should be easy to compare. We should check cards when opening tables, as agreed for the guest trial.

**Thanh:** I worry about an offline scan opening a table locally while someone else has already changed that table on another device. After the connection returns, we cannot have two bills and two kitchen tickets for one group. That is the same sort of clean-up that hurt us in August.

**Đức:** If every staff order and guest addition comes through the same card, I still need to tell which was confirmed by a server and which is waiting for one. A guest phone order must not look like a kitchen instruction before Lan’s team has checked it.

**Mai Anh:** My concern is that “scan the QR” becomes one more chore instead of making service easier. Let servers practise it on all 24 tables, but judge the six-table guest trial separately. On a cold, busy evening I will care whether the right food reaches the right group and the bill is correct, not how modern the cards look.
