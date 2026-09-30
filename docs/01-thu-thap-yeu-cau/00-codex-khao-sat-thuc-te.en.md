# Codex field research brief (raw, English)

> Nguồn: Codex (GPT, web search live), 29/09/2026. Bản gốc tiếng Anh, chưa chỉnh sửa. Mục UNVERIFIED = giả định, chưa kiểm chứng.

*Research snapshot: September 2026. Figures labelled **UNVERIFIED** are useful scenario assumptions, not measured Vietnam-wide averages.*

**1. Business models and scale**

- Vietnam’s market includes small family restaurants, beer-and-food venues (*quán nhậu*), hotpot and BBQ restaurants, buffets, and growing chains. An [iPOS/Nestlé 2025 survey](https://ipos.vn/thong-cao-bao-chi-ipos-vn-va-nestle-professional-cong-bo-bao-cao-thi-truong-kinh-doanh-am-thuc-tai-viet-nam-nam-2025/) estimated 329,500 F&B outlets and reported strong pressure from ingredient costs. Its [first-half 2025 consumer survey](https://ipos.vn/en/press-release-iposvn-and-nestle-professional-announce-the-vietnam-foodservice-business-market-report-for-the-first-half-of-2025/) found more dinner spending above VND 100,000 per person.
- **UNVERIFIED:** I could not establish dependable national norms for tables, seats, daily covers, staffing, opening hours, or average bills *by restaurant model*. For a realistic small-chain simulation, a 20–30-table outlet, lunch and dinner service, and a roughly VND 200,000–300,000 dine-in spend per person are plausible assumptions, not benchmarks. Role mixes commonly need an outlet manager, cashier, servers, kitchen staff, and cleaning/prep support; the exact count depends heavily on service style.

**2. Front of house, kitchen, and payment**

- A typical service path is reservation or walk-in seating, order capture, kitchen/bar preparation, serving, additions or cancellations, bill handling, and shift close. Modern products support handheld or QR orders, kitchen display systems (KDS), and table transfer, merge, and split functions; their existence does **not** mean every restaurant has adopted them. Paper tickets and verbal changes remain plausible where processes are only partly digitised. [KiotViet restaurant product](https://www.kiotviet.vn/quan-ly-nha-hang), [Sapo FnB workflow](https://fnb.sapo.vn/phan-mem-quan-ly-nha-hang)
- Restaurants may collect cash, bank transfers by QR, cards, and wallets, sometimes across one bill. Shift close therefore needs separate cash counts and reconciliation of non-cash receipts, refunds, discounts, and voids. [iPOS FABi](https://posfabi.ipos.vn/phan-mem-quan-ly-ban-hang) lists cash, cards, MoMo, ZaloPay and VNPAY; [Sapo FnB](https://fnb.sapo.vn/phan-mem-quan-ly-nha-hang) describes cash, transfer and QR payment.
- Takeaway and app delivery add another order and settlement stream. [GrabFood](https://www.grab.com/vn/merchant/food/) provides a merchant app and transfers net proceeds after contractual fees; [ShopeeFood](https://merchant.shopeefood.vn/edu/course/lam-quen-cac-cong-cu-ban-hang/quan-ly-don-hang) provides merchant order tools; [beFood](https://beacademy.be.com.vn/merchant) has its own merchant workflow. Integration varies by POS and commercial arrangement.

**3. Inventory and purchasing**

- Menu sales alone do not show ingredient usage. Standard portions (*định lượng*) connect dishes to expected consumption and food cost; counts then expose differences caused by trimming, spoilage, substitutions, over-portioning, or missing records. [Sapo FnB](https://fnb.sapo.vn/phan-mem-quan-ly-nha-hang) describes recipe-based stock deduction and low-stock alerts; [KiotViet](https://www.kiotviet.vn/quan-ly-nha-hang) also describes recipe deduction and comparisons of ingredient cost with sales.
- In a small chain, purchasing also means supplier orders, delivery checks, purchase prices, unpaid invoices, and stock transfers. A shared prep kitchen can turn raw ingredients into sauces or portioned meat before sending them to outlets; [MISA CukCuk’s transfer guidance](https://helpv2.cukcuk.vn/vi/kb/r120) documents inter-restaurant stock receipts. **UNVERIFIED:** There is no sound universal food-cost percentage for all Vietnamese restaurant formats; the target should be tested against the actual menu, waste, and purchasing records.

**4. People and controls**

- Service peaks drive staggered shifts and part-time staffing. Timekeeping, overtime, allowances, and payroll may sit outside the sales POS; [iPOS](https://ipos.vn/san-pham/fabibox-mobile/) offers HRM separately for shifts, attendance, and pay. Permissions matter because servers, managers, cashiers, chefs, and accountants need different powers.
- Potential leakage points include an unrecorded cash sale, a void after food has been served, an unsupported discount, and stock leaving without a recorded transfer. These are *risks to investigate*, not proof of fraud. Vendor tools illustrate the controls: [iPOS FABi](https://posfabi.ipos.vn/phan-mem-quan-ly-ban-hang) logs bill edits and order activity; [KiotViet](https://www.kiotviet.vn/quan-ly-nha-hang) reports cancellations and shift results. Fake discounts and ingredient theft as prevalent national patterns are **UNVERIFIED**.

**5. Customers and marketing**

- Restaurants use repeat-customer records, points or membership tiers, vouchers, birthday offers, and time-limited promotions. [KiotViet](https://www.kiotviet.vn/quan-ly-nha-hang) shows customer tiers and points; [iPOS FABi](https://posfabi.ipos.vn/phan-mem-quan-ly-ban-hang) supports conditional vouchers and discounts. Delivery platforms also offer merchant advertising and campaigns ([Grab merchant guidance](https://www.grab.com/vn/en/merchant-grabacademy/)).
- Customer phone numbers and purchase histories can become fragmented across outlets, POS accounts, and apps. Online reviews can flag service failures, but I did not find an authoritative measure of how frequently small restaurants formally track them: **UNVERIFIED**.

**6. Owner reports**

- Useful daily views include sales by outlet, shift and payment method; cash differences; discounts and voids; best sellers; and delivery sales after fees. Weekly or monthly views add purchase prices, stock variance, labour and occupancy costs, and outlet profit and loss. [KiotViet](https://www.kiotviet.vn/quan-ly-nha-hang) advertises shift, dish, cost, and branch comparisons; [iPOS](https://ipos.vn/san-pham/fabibox-mobile/) lists daily revenue, best sellers, hourly bills, and cash-in/out reports. A POS sales report alone is not a reliable outlet P&L unless costs and settlement data are complete.

**7. Tax, invoices, and personal data**

- [Decree 70/2025](https://xaydungchinhsach.chinhphu.vn/mot-so-noi-dung-moi-cua-nghi-dinh-so-70-2025-nd-cp-ve-hoa-don-chung-tu-119250403074719995.htm) amended Decree 123/2020 and brought restaurants and qualifying household businesses into the POS-origin e-invoice framework. The position has continued to evolve: [Decree 254/2026](https://xaydungchinhsach.chinhphu.vn/toan-van-nghi-dinh-so-254-2026-nd-cp-ve-hoa-don-dien-tu-chung-tu-dien-tu-119260713164251972.htm) took effect on 1 July 2026. Invoice handling needs checking against the **current** rules and the business’s tax method.
- The general 10% VAT rate is reduced to **8% through 31 December 2026 for eligible goods and services**, with exclusions; item-level classification and the seller’s VAT method matter. A household business should not simply copy a company’s invoice calculation. [National Assembly VAT resolution summary](https://xaydungchinhsach.chinhphu.vn/thong-qua-nghi-quyet-cua-quoc-hoi-ve-giam-thue-gia-tri-gia-tang-119250617102022144.htm), [implementing decree](https://datafiles.chinhphu.vn/cpp/files/vbpq/2025/7/174nd.signed.pdf)
- Household businesses moved away from lump-sum tax from **1 January 2026**; [Decree 68/2026](https://xaydungchinhsach.chinhphu.vn/toan-van-nghi-dinh-68-2026-nd-cp-quy-dinh-ve-chinh-sach-thue-quan-ly-thue-voi-ho-kinh-doanh-119260306102906789.htm) governs current declaration arrangements. For personal data, Decree 13/2023 is historical context: the [Personal Data Protection Law’s implementing Decree 356/2025](https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-356-2025-nd-cp-468371.htm) took effect on 1 January 2026 and replaced Decree 13.

**8. Existing software and reasons to seek custom work**

- [KiotViet FnB](https://www.kiotviet.vn/quan-ly-nha-hang), [Sapo FnB](https://fnb.sapo.vn/phan-mem-fnb), [iPOS FABi](https://posfabi.ipos.vn/phan-mem-quan-ly-ban-hang), [MISA CukCuk](https://store.misa.vn/quote?pid=210), [POS365](https://www.pos365.vn/quan-ly-nhan-vien-lam-viec-tu-xa-5817.html), and [Ocha](https://ocha.vn/) offer overlapping combinations of ordering, stock, customers, reporting, and invoicing. Sapo advertises plans around **VND 249,000–399,000 per outlet/month**, subject to term and package; equipment and add-ons may be separate.
- Published vendor pages identify pain around rush-hour errors, stock visibility, and fragmented channels, but they are marketing evidence, not an independent ranking of owner complaints. **UNVERIFIED:** Claims that owners generally dislike a particular vendor, or that custom software is cheaper, would need interviews and quotes. A credible custom-software motive is a chain-specific process—such as shared prep stock, approval rules, and reconciliation across sales channels—that the owner cannot make work acceptably in an off-the-shelf package.

