import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';
import { API_BASE_URL } from './Config';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, LineChart, Line, Legend,
} from 'recharts';
import './Books.css';

const MORANDI = [
  '#7b9db5', '#c4976b', '#81a88b', '#c9a874', '#7d9a82',
  '#b09b7d', '#968ba6', '#7ba392', '#b8977e', '#9b8aaf',
  '#7a9ea8', '#af8297', '#95a87c', '#b09882', '#7b99ae',
];

const OverviewCard = ({ label, value, sub }) => (
  <div style={{
    background: '#fff', borderRadius: 16, padding: '20px 24px',
    boxShadow: '0 2px 8px rgba(0,0,0,0.06)', minWidth: 140,
  }}>
    <div style={{ fontSize: 13, color: '#86868b', fontWeight: 500, marginBottom: 4 }}>{label}</div>
    <div style={{ fontSize: 28, fontWeight: 700, color: '#1d1d1f' }}>{value}</div>
    {sub && <div style={{ fontSize: 12, color: '#86868b', marginTop: 4 }}>{sub}</div>}
  </div>
);

const ChartCard = ({ title, children }) => (
  <div style={{
    background: '#fff', borderRadius: 16, padding: '20px 24px',
    boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
  }}>
    <h3 style={{ margin: '0 0 16px', fontSize: 17, fontWeight: 600, color: '#1d1d1f' }}>{title}</h3>
    {children}
  </div>
);

const ColorLegend = ({ data, colors }) => (
  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px 16px', marginTop: 12, justifyContent: 'center' }}>
    {data.map((entry, i) => (
      <div key={entry.name} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: '#86868b' }}>
        <div style={{ width: 10, height: 10, borderRadius: 3, background: colors[i % colors.length], flexShrink: 0 }} />
        <span>{entry.name} ({entry.count})</span>
      </div>
    ))}
  </div>
);

const TimeControls = ({ timeMode, setTimeMode, selectedYear, setSelectedYear, availableYears }) => (
  <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 16 }}>
    <select value={selectedYear} onChange={(e) => setSelectedYear(e.target.value)}
      style={{ padding: '4px 8px', borderRadius: 6, border: '1px solid #d2d2d7', fontSize: 12, outline: 'none' }}>
      <option value="all">All</option>
      {availableYears.map(y => <option key={y} value={y}>{y}</option>)}
    </select>
    <select value={timeMode} onChange={(e) => { setTimeMode(e.target.value); setSelectedYear('all'); }}
      style={{ padding: '4px 8px', borderRadius: 6, border: '1px solid #d2d2d7', fontSize: 12, outline: 'none' }}>
      <option value="month">By Month</option>
      <option value="year">By Year</option>
    </select>
  </div>
);

const StatsPage = () => {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [timeMode, setTimeMode] = useState('month');
  const [selectedYear, setSelectedYear] = useState('all');

  useEffect(() => {
    axios.get(`${window.location.origin}${API_BASE_URL}/stats/books`)
      .then(res => setStats(res.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const availableYears = useMemo(() => {
    if (!stats) return [];
    const allLabels = [
      ...(stats.timeline_years || []),
      ...(stats.purchase_years || []),
    ];
    return [...new Set(allLabels.map(d => d.year))].sort();
  }, [stats]);

  const filteredTimeline = useMemo(() => {
    if (!stats) return [];
    const source = timeMode === 'year' ? stats.timeline_years : stats.timeline_months;
    if (!source) return [];
    if (selectedYear === 'all') return source;
    return source.filter(d => d.year === parseInt(selectedYear));
  }, [stats, timeMode, selectedYear]);

  const filteredPurchase = useMemo(() => {
    if (!stats) return [];
    const source = timeMode === 'year' ? stats.purchase_years : stats.purchase_months;
    if (!source) return [];
    if (selectedYear === 'all') return source;
    return source.filter(d => d.year === parseInt(selectedYear));
  }, [stats, timeMode, selectedYear]);

  if (loading) return <div className="loading">Loading statistics...</div>;
  if (!stats) return <div className="error">Failed to load statistics</div>;

  const { overview } = stats;
  const discountPct = overview.total_spent && overview.avg_price * overview.total_books > 0
    ? (1 - overview.total_spent / (overview.avg_price * overview.total_books)) * 100
    : 0;

  // Scrollable chart height: tall enough to show all items
  const catHeight = Math.max(300, (stats.by_category || []).length * 28);
  const authHeight = Math.max(300, (stats.top_authors || []).length * 28);
  const pubHeight = Math.max(300, (stats.top_publishers || []).length * 28);

  return (
    <section className="section light">
      <div className="container" style={{ maxWidth: 1200 }}>
        <h1 className="section-heading">Statistics</h1>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: 16, marginBottom: 32 }}>
          <OverviewCard label="Total Books" value={overview.total_books} />
          <OverviewCard label="Total Authors" value={overview.total_authors} />
          <OverviewCard label="Total Publishers" value={overview.total_publishers} />
          <OverviewCard label="Categories" value={overview.total_categories} />
          <OverviewCard label="Average Price" value={`¥${overview.avg_price}`} />
          <OverviewCard label="Total Spent" value={`¥${overview.total_spent}`} sub={discountPct > 0 ? `~${discountPct.toFixed(0)}% off cover` : ''} />
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: 24 }}>

          {/* Read State */}
          {stats.by_read_state?.length > 0 && (
            <ChartCard title="Books by Read State">
              <ResponsiveContainer width="100%" height={250}>
                <PieChart>
                  <Pie data={stats.by_read_state} dataKey="count" nameKey="name" cx="50%" cy="45%" outerRadius={90}>
                    {stats.by_read_state.map((_, i) => <Cell key={i} fill={MORANDI[i % MORANDI.length]} />)}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
              <ColorLegend data={stats.by_read_state} colors={MORANDI} />
            </ChartCard>
          )}

          {/* Category — full scroll */}
          {stats.by_category?.length > 0 && (
            <ChartCard title="Books by Category">
              <div style={{ maxHeight: 400, overflowY: 'auto' }}>
                <BarChart data={stats.by_category} layout="vertical" width={450} height={catHeight} margin={{ left: 100 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis type="number" />
                  <YAxis type="category" dataKey="name" width={100} tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Bar dataKey="count" fill={MORANDI[0]} radius={[0, 4, 4, 0]} />
                </BarChart>
              </div>
            </ChartCard>
          )}

          {/* Binding Type */}
          {stats.by_binding?.length > 0 && (
            <ChartCard title="Books by Binding Type">
              <ResponsiveContainer width="100%" height={250}>
                <PieChart>
                  <Pie data={stats.by_binding} dataKey="count" nameKey="name" cx="50%" cy="45%" outerRadius={90}>
                    {stats.by_binding.map((_, i) => <Cell key={i} fill={MORANDI[i % MORANDI.length]} />)}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
              <ColorLegend data={stats.by_binding} colors={MORANDI} />
            </ChartCard>
          )}

          {/* Language */}
          {stats.by_language?.length > 0 && (
            <ChartCard title="Books by Language">
              <ResponsiveContainer width="100%" height={250}>
                <BarChart data={stats.by_language}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                  <YAxis />
                  <Tooltip />
                  <Bar dataKey="count" fill={MORANDI[2]} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
          )}

          {/* Top Authors — full scroll */}
          {stats.top_authors?.length > 0 && (
            <ChartCard title="Top Authors by Book Count">
              <div style={{ maxHeight: 400, overflowY: 'auto' }}>
                <BarChart data={stats.top_authors} layout="vertical" width={450} height={authHeight} margin={{ left: 100 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis type="number" />
                  <YAxis type="category" dataKey="name" width={100} tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Bar dataKey="count" fill={MORANDI[6]} radius={[0, 4, 4, 0]} />
                </BarChart>
              </div>
            </ChartCard>
          )}

          {/* Top Publishers — full scroll */}
          {stats.top_publishers?.length > 0 && (
            <ChartCard title="Top Publishers by Book Count">
              <div style={{ maxHeight: 400, overflowY: 'auto' }}>
                <BarChart data={stats.top_publishers} layout="vertical" width={450} height={pubHeight} margin={{ left: 120 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis type="number" />
                  <YAxis type="category" dataKey="name" width={120} tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Bar dataKey="count" fill={MORANDI[4]} radius={[0, 4, 4, 0]} />
                </BarChart>
              </div>
            </ChartCard>
          )}

          {/* Books Added Over Time */}
          {filteredTimeline.length > 0 && (
            <ChartCard title="Books Added Over Time">
              <TimeControls
                timeMode={timeMode} setTimeMode={setTimeMode}
                selectedYear={selectedYear} setSelectedYear={setSelectedYear}
                availableYears={availableYears}
              />
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={filteredTimeline}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                  <YAxis allowDecimals={false} />
                  <Tooltip />
                  <Line type="monotone" name="Books" dataKey="count" stroke={MORANDI[0]} strokeWidth={2} dot={{ r: 4 }} />
                </LineChart>
              </ResponsiveContainer>
            </ChartCard>
          )}

          {/* Purchases Over Time */}
          {filteredPurchase.length > 0 && (
            <ChartCard title="Purchases Over Time">
              <TimeControls
                timeMode={timeMode} setTimeMode={setTimeMode}
                selectedYear={selectedYear} setSelectedYear={setSelectedYear}
                availableYears={availableYears}
              />
              <ResponsiveContainer width="100%" height={280}>
                <LineChart data={filteredPurchase}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                  <YAxis yAxisId="left" allowDecimals={false} />
                  <YAxis yAxisId="right" orientation="right" tickFormatter={(v) => `¥${v}`} />
                  <Tooltip formatter={(v, name) => name === 'Cost' ? `¥${v}` : v} />
                  <Legend />
                  <Line yAxisId="left" type="monotone" name="Books" dataKey="count" stroke={MORANDI[0]} strokeWidth={2} dot={{ r: 4 }} />
                  <Line yAxisId="right" type="monotone" name="Cost" dataKey="price" stroke={MORANDI[4]} strokeWidth={2} dot={{ r: 4 }} strokeDasharray="6 4" />
                </LineChart>
              </ResponsiveContainer>
            </ChartCard>
          )}

          {/* Douban Score */}
          {stats.by_score?.length > 0 && (
            <ChartCard title="Douban Score Distribution">
              <ResponsiveContainer width="100%" height={250}>
                <BarChart data={stats.by_score}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                  <YAxis allowDecimals={false} />
                  <Tooltip />
                  <Bar dataKey="count" fill={MORANDI[3]} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
          )}

          {/* Compose Type */}
          {stats.by_compose?.length > 0 && (
            <ChartCard title="Books by Compose Type">
              <ResponsiveContainer width="100%" height={250}>
                <PieChart>
                  <Pie data={stats.by_compose} dataKey="count" nameKey="name" cx="50%" cy="45%" outerRadius={90}>
                    {stats.by_compose.map((_, i) => <Cell key={i} fill={MORANDI[i % MORANDI.length]} />)}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
              <ColorLegend data={stats.by_compose} colors={MORANDI} />
            </ChartCard>
          )}

        </div>
      </div>
    </section>
  );
};

export default StatsPage;
