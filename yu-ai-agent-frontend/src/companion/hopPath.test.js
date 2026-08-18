import test from 'node:test'
import assert from 'node:assert/strict'

import { CHAIR_POSE, DOOR_POSE, planHopTrip, poseTransform } from './hopPath.js'

function seeded(seed) {
  let value = seed
  return () => {
    value = (value * 16807) % 2147483647
    return (value - 1) / 2147483646
  }
}

test('every hop trip starts on the chair and ends at the door', () => {
  for (let seed = 1; seed <= 12; seed += 1) {
    const trip = planHopTrip(CHAIR_POSE, DOOR_POSE, seeded(seed * 97))
    assert.equal(trip.keyframes[0].transform, poseTransform(CHAIR_POSE))
    assert.equal(trip.keyframes.at(-1).transform, poseTransform(DOOR_POSE))
    assert.ok(trip.hops >= 2)
    assert.ok(trip.duration >= 600)
    assert.ok(trip.keyframes.length >= 5)
  }
})

test('coming back always lands on the chair', () => {
  const trip = planHopTrip(DOOR_POSE, CHAIR_POSE, seeded(42))
  assert.equal(trip.keyframes.at(-1).transform, poseTransform(CHAIR_POSE))
})

test('different seeds pick different routes', () => {
  const a = planHopTrip(CHAIR_POSE, DOOR_POSE, seeded(3))
  const b = planHopTrip(CHAIR_POSE, DOOR_POSE, seeded(11))
  assert.notEqual(a.keyframes[2].transform, b.keyframes[2].transform)
})
