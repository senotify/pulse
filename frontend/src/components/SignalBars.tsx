import type { Features } from '../api/types';

// Labels and weights mirror pulse.recommendation.weights in the API's application.yml.
const SIGNALS: { key: keyof Features; label: string; weight: number }[] = [
  { key: 'inNetwork', label: 'You follow them', weight: 1.0 },
  { key: 'authorAffinity', label: 'Your past engagement', weight: 2.0 },
  { key: 'socialProof', label: 'Engaged by people you follow', weight: 1.2 },
  { key: 'engagementVelocity', label: 'Engagement velocity', weight: 1.5 },
  { key: 'recency', label: 'Recency', weight: 1.0 },
  { key: 'authorReputation', label: 'Author reach', weight: 0.3 },
  { key: 'contentAffinity', label: 'Matches your likes', weight: 0.8 },
];

/** Shows why the ranker scored a post as it did: each normalized feature and the final score. */
export function SignalBars({ features, score }: { features: Features; score: number }) {
  return (
    <div className="signals" onClick={(e) => e.stopPropagation()}>
      {SIGNALS.map(({ key, label, weight }) => (
        <Signal key={key} label={`${label} ×${weight}`} value={features[key]} />
      ))}
      <div className="total">
        <span>Score</span>
        <span>{score.toFixed(3)}</span>
      </div>
    </div>
  );
}

function Signal({ label, value }: { label: string; value: number }) {
  return (
    <>
      <span>{label}</span>
      <span className="bar">
        <span style={{ width: `${Math.round(value * 100)}%` }} />
      </span>
      <span className="value">{value.toFixed(2)}</span>
    </>
  );
}
