# intuit-design-exercise
Building the backend for a network of parking garages. Drivers reserve spots, check in when they arrive, and check out when they leave. Operators need an accurate view of which spots are free, held, or occupied.

# Things to Consider
- A spot should only be in one meaningful state at a time, but many drivers may act at once
- Reservations may expire if the driver never arrives
- Spots may go offline for maintenance without losing history
- Availability should stay trustworthy when many spots change state quickly****
